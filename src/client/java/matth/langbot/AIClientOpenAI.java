package matth.langbot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AIClientOpenAI implements AiClient {
    private boolean isConnectionOk = false;

    private static String ENDPOINT_CHAT = "/v1/chat/completions";
    private static String ENDPOINT_MODELS = "/v1/models";

    private static String VISION_MODEL = "gpt-4o-mini";
    private static String CHAT_MODEL = "gpt-4o-mini";

    private final Logger LOGGER;

    public AIClientOpenAI(Logger logger) {
        LOGGER = logger;
    }

    private Request.Builder getOpenAiRequest(String endpoint) {
        return new Request.Builder()
                .url("https://api.openai.com" + endpoint)
                .header("Authorization", "Bearer " + LangBotConfig.HANDLER.instance().openAiKey);
    }

    @Override
    public String executePrompt(String prompt, Optional<Path> imagePath) throws IOException {
        // Build json body.
        JsonObject textPromptObject = new JsonObject();
        textPromptObject.addProperty("type", "text");
        textPromptObject.addProperty("text", prompt);

        JsonArray messageContentArray = new JsonArray();
        messageContentArray.add(textPromptObject);

        if (!imagePath.isEmpty()) {
            byte[] imageBytes = Files.readAllBytes(imagePath.get());
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            JsonObject imageUrlObject = new JsonObject();
            imageUrlObject.addProperty("detail", "low");
            imageUrlObject.addProperty("url", "data:image/png;base64," + base64Image);

            JsonObject imagePromptObject = new JsonObject();
            imagePromptObject.addProperty("type", "image_url");
            imagePromptObject.add("image_url", imageUrlObject);
            messageContentArray.add(imagePromptObject);
        }

        JsonObject messageObject = new JsonObject();
        messageObject.addProperty("role", "user");
        messageObject.add("content", messageContentArray);

        JsonArray messagesArray = new JsonArray();
        messagesArray.add(messageObject);

        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("model",  imagePath.isEmpty() ? CHAT_MODEL : VISION_MODEL);
        jsonObject.addProperty("store", false);
        jsonObject.add("messages", messagesArray);

        // Send request.
        OkHttpClient client = new OkHttpClient.Builder().build();
        Response response = client.newCall(this.getOpenAiRequest(ENDPOINT_CHAT).post(RequestBody.Companion.create(jsonObject.toString(), MediaType.get("application/json"))).build()).execute();
        if (response.body() == null) {
            response.close();
            throw new IOException("No body returned");
        }
        if (!response.isSuccessful()) {
            String err = "Unexpected code " + response.code() + response.body().string();
            response.close();
            throw new IOException(err);
        }

        JsonObject jsonResponse = JsonParser.parseString(response.body().string()).getAsJsonObject();
        response.body().close();

        return jsonResponse.getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message").get("content").getAsString();
    }

    @Override
    public boolean isConnectionOk() {
        return isConnectionOk;
    }

    @Override
    public void requestConnectionCheck() {
        OkHttpClient client = new OkHttpClient.Builder().build();
        try {
            Response response = client.newCall(this.getOpenAiRequest(ENDPOINT_MODELS).get().build()).execute();
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
            if (response.body() == null) throw new IOException("No body");
            JsonObject json = JsonParser.parseString(response.body().string()).getAsJsonObject();

            // Ensure the required models are in the list of available models.
            List<String> availableModels = json.getAsJsonArray("data").asList().stream().map(e -> e.getAsJsonObject().get("id").getAsString()).collect(Collectors.toList());
            if (!availableModels.contains(VISION_MODEL)) {
                LOGGER.error("Required vision model {} not found", VISION_MODEL);
                isConnectionOk = false;
                return;
            }

            if (!availableModels.contains(CHAT_MODEL)) {
                LOGGER.error("Required chat model {} not found", CHAT_MODEL);
                isConnectionOk = false;
                return;
            }

            // All checks passed.
            LOGGER.info("OpenAI Client connection check passed");
            isConnectionOk = true;
        } catch (IOException e) {
            LOGGER.error(e.toString());
            isConnectionOk = false;
        }
    }
}
