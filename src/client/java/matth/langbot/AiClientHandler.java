package matth.langbot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import okhttp3.*;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class AiClientHandler {
    private final Logger LOGGER;
    private final MinecraftClient client;

    static final String ImageToTextModel = "llava:7b";
    static final String TextToTextModel = "llama3:8b";

    public static final String[] requiredModels = new String[] {
            ImageToTextModel,
            TextToTextModel,
    };

    private HashMap<String, Result<String>> componentStatuses = new HashMap<String, Result<String>>();

    public AiClientHandler(Logger logger, MinecraftClient client) {
        this.LOGGER = logger;
        this.client = client;

        for (String model: requiredModels) {
            componentStatuses.put(model, new Result.Loading<>());
        }
    }

    public Result<String> getModelStatus(String model) {
        return componentStatuses.getOrDefault(model, new Result.Error<>("Unexpected model " +  model));
    }

    public String convertScreenshotToText(String filename) {
        String screenshotPath = client.runDirectory.getAbsolutePath() + "\\screenshots\\" + filename;
        return this.callGenerateAndGetResponse(ImageToTextModel,
                "Explain what is shown in this minecraft world in 1 sentence with a maximum of 7 words.",
                new String[]{ screenshotPath }
        );
    }

    public String translateDescriptionIntoLanguage(String language, String description) {
        return this.callGenerateAndGetResponse(TextToTextModel, "Translate this into " + language + ", and do not say anything else: " + description);
    }

    private String callGenerateAndGetResponse(String model, String prompt) {
        return this.callGenerateAndGetResponse(model, prompt, new String[]{});
    }

    private OkHttpClient getClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
    }

    private String callGenerateAndGetResponse(String model, String prompt, String[] imagePaths) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("model", model);
        jsonObject.addProperty("prompt", prompt);
        jsonObject.addProperty("stream", false);

        if  (imagePaths.length >  0) {
            JsonArray images = new JsonArray();

            try {
                for (String imagePath: imagePaths) {
                    byte[] imageBytes = Files.readAllBytes(Paths.get(imagePath));
                    String base64Image = Base64.getEncoder().encodeToString(imageBytes);
                    images.add(base64Image);
                }
            } catch (Exception e) {
                LOGGER.error(e.toString());
            }

            jsonObject.add("images", images);
        }

        Request request = new Request.Builder()
                .url("http://localhost:11434/api/generate")
                .post(RequestBody.create(MediaType.parse("text/json"), jsonObject.toString()))
                .build();

        // Call and get the response.
        try (Response response = this.getClient().newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

            JsonObject o = JsonParser.parseString(response.body() != null ? response.body().string() : "{}").getAsJsonObject();
            return o.get("response").toString();
        } catch (IOException e) {
            return "";
        }
    }

    private List<String> getAvailableModels() {
        Request request = new Request.Builder()
                .url("http://localhost:11434/api/tags")
                .get()
                .build();

        // Call and get the response.
        try (Response response = this.getClient().newCall(request).execute()) {
            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
            if (response.body() == null) throw new Exception("Invalid response");

            List<String> models = new ArrayList<>();

            JsonObject o = JsonParser.parseString(response.body().string()).getAsJsonObject();
            for (JsonElement item: o.getAsJsonArray("models")) {
                models.add(item.getAsJsonObject().get("name").getAsString());
            }
            return models;
        } catch (Exception e) {
            LOGGER.error(e.toString());
            return new ArrayList<>();
        }
    }

    public void updateAvailableModels() {
        // Update model availability info.
        for (String requiredModel: requiredModels) {
            componentStatuses.put(requiredModel, new Result.Loading<>());
        }

        List<String> availableModels = this.getAvailableModels();

        for (String requiredModel: requiredModels) {
            if (availableModels.contains(requiredModel)) {
                componentStatuses.put(requiredModel, new Result.Ok<>("Model " + requiredModel + " available"));
            } else {
                componentStatuses.put(requiredModel, new Result.Error<>("Model " + requiredModel + " not available"));
            }
        }
    }
}
