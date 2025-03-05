package matth.langbot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class AIClientOpenAI implements AiClientBase, AiSessionClient {
    private boolean isConnectionOk = false;

    private static String ENDPOINT_FILES = "/v1/files";
    private static String ENDPOINT_CHAT = "/v1/chat/completions";
    private static String ENDPOINT_MODELS = "/v1/models";
    private static String ENDPOINT_ASSISTANTS = "/v1/assistants";
    private static String ENDPOINT_THREADS = "/v1/threads";

    private static String MODEL = "gpt-4o-mini";

    private final Logger LOGGER;

    private int tokensUsed = 0;

    private String assistantId;

    private String threadId;

    public AIClientOpenAI(Logger logger) {
        LOGGER = logger;
    }

    public int getTokensUsed() {
        return this.tokensUsed;
    }

    private Request.Builder getOpenAiRequest(String endpoint) {
        return new Request.Builder()
                .url("https://api.openai.com" + endpoint)
                .header("Authorization", "Bearer " + LangBotConfig.HANDLER.instance().openAiKey)
                .header("OpenAI-Beta", "assistants=v2");
    }

    private JsonObject callAndGetJson(Request req) throws IOException {
        OkHttpClient client = new OkHttpClient.Builder().build();
        Response response = client.newCall(req).execute();
        if (response.body() == null) {
            response.close();
            throw new IOException("No newAssistantBody returned");
        }
        if (!response.isSuccessful()) {
            String err = "Unexpected code " + response.code() + response.body().string();
            response.close();
            throw new IOException(err);
        }
        JsonObject jsonResponse = JsonParser.parseString(response.body().string()).getAsJsonObject();
        response.body().close();
        return jsonResponse;
    }

    @Override
    public boolean isConnectionOk() {
        return isConnectionOk;
    }

    @Override
    public void requestConnectionCheck() {
        try {
            JsonObject json = this.callAndGetJson(this.getOpenAiRequest(ENDPOINT_MODELS).get().build());

            // Ensure the required models are in the list of available models.
            List<String> availableModels = json.getAsJsonArray("data").asList().stream().map(e -> e.getAsJsonObject().get("id").getAsString()).collect(Collectors.toList());
            if (!availableModels.contains(MODEL)) {
                LOGGER.error("Required model {} not found", MODEL);
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

    @Override
    public void startNewSession() {
        try {
            // Start a new assistant.
            JsonElement newAssistantBody = JsonParser.parseString("""
                    {
                    "instructions": "Respond with one or two A1/A2 level German sentences.",
                    "name": "German teacher",
                    "model": %s
                    }
                    """.formatted(MODEL));
            JsonObject assistantResponse = this.callAndGetJson(this.getOpenAiRequest(ENDPOINT_ASSISTANTS).post(RequestBody.Companion.create(newAssistantBody.toString(), MediaType.get("application/json"))).build());
            assistantId = assistantResponse.get("id").getAsString();
            LOGGER.info("New assistant created {}", assistantId);

            // Make a new thread.
            JsonObject threadResponse = this.callAndGetJson(this.getOpenAiRequest(ENDPOINT_THREADS).post(RequestBody.Companion.create(new byte[0])).build());
            threadId = threadResponse.get("id").getAsString();
            LOGGER.info("New thread created {}", threadId);

            // This assistant tends to forget instructions, so constantly remind it.
            //this.addInstructionToSession("");
        } catch (Exception e) {
            LOGGER.error(e.toString());
            isConnectionOk = false;
        }
    }

    private void ensureSessionStarted() {
        if (this.threadId == null) {
            this.startNewSession();
        }
    }

    @Override
    public void addImageToSession(Path imagePath) throws Exception {
        this.ensureSessionStarted();

        // First we need to upload it as a file.
        // (assistant api does not support receiving base64 images currently).
        File file = imagePath.toFile();
        RequestBody fileBody = RequestBody.create(file, MediaType.parse("application/octet-stream"));

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("purpose", "vision")
                .addFormDataPart("file", file.getName(), fileBody)
                .build();

        // TODO we should probably clean up these files at some stage.
        // since storing them costs $$$$
        JsonObject res = this.callAndGetJson(this.getOpenAiRequest(ENDPOINT_FILES).post(requestBody).build());
        String fileid = res.get("id").getAsString();
        LOGGER.info("Uploaded file {}", fileid);

        // Add this file in a message to the thread.
        JsonElement newMessageBody = JsonParser.parseString("""
                {
                    "role": "user",
                    "content": [
                        {
                            "type": "image_file",
                            "image_file": {
                                "detail": "low",
                                "file_id": "%s"
                            }
                        }
                    ]
                }
                """.formatted(fileid));
        String path = "%s/%s/messages".formatted(ENDPOINT_THREADS, threadId);
        JsonObject newMessageResponse = this.callAndGetJson(this.getOpenAiRequest(path).post(RequestBody.Companion.create(newMessageBody.toString(), MediaType.get("application/json"))).build());
        String id = newMessageResponse.get("id").getAsString();
        LOGGER.info("Image message added to thread {}", id);
    }

    @Override
    public void addChatToSession(String chat) throws Exception {
        this.addMessage(chat, "user");
    }

    @Override
    public void addInstructionToSession(String chat) throws Exception {
        this.addMessage(chat, "assistant");
    }

    private void addMessage(String chat, String role) throws Exception {
        this.ensureSessionStarted();

        JsonElement newMessageBody = JsonParser.parseString("""
            {
                "role": "%s",
                "content": "%s"
            }
            """.formatted(role, chat));
        String path = "%s/%s/messages".formatted(ENDPOINT_THREADS, threadId);
        JsonObject newMessageResponse = this.callAndGetJson(this.getOpenAiRequest(path).post(RequestBody.Companion.create(newMessageBody.toString(), MediaType.get("application/json"))).build());
        String id = newMessageResponse.get("id").getAsString();
        LOGGER.info("Chat message added to thread {}", id);
    }

    @Override
    public String runSession() throws Exception {


        String endpoint = "%s/%s/runs".formatted(ENDPOINT_THREADS, threadId);
        String body = """
            {
            "assistant_id": "%s",
            "tool_choice": "none"
            }
        """.formatted(assistantId);
        JsonObject runSessionResponse = this.callAndGetJson(this.getOpenAiRequest(endpoint).post(RequestBody.Companion.create(body, MediaType.get("application/json"))).build());
        String runId = runSessionResponse.get("id").getAsString();
        LOGGER.info("Run started {}", runId);

        // TODO set max tokens per run.

        // Poll the run until it is done.
        while (true) {
            String pollEndpoint = "%s/%s/runs/%s".formatted(ENDPOINT_THREADS, threadId, runId);
            JsonObject statusRes = this.callAndGetJson(this.getOpenAiRequest(pollEndpoint).get().build());
            String status = statusRes.get("status").getAsString();

            // Not done yet.
            if (Objects.equals(status, "queued") || Objects.equals(status, "in_progress")) {
                Thread.sleep(500);
                continue;
            }

            // Otherwise is done.
            if (Objects.equals(status, "completed")) {
                int total_tokens = statusRes.getAsJsonObject("usage").get("total_tokens").getAsInt();
                this.tokensUsed += total_tokens;
                break;
            }

            // Else is broken.
            throw new Exception("Run status is %s".formatted(status));
        }

        LOGGER.info("Run complete!");

        // Now get the latest message from the chatbot on the thread.
        String getLatestMessageEndpoint = "%s/%s/messages?limit=1&order=desc&run_id=%s".formatted(ENDPOINT_THREADS, threadId, runId);
        JsonObject latestMessageRes = this.callAndGetJson(this.getOpenAiRequest(getLatestMessageEndpoint).get().build());
        String res = latestMessageRes.getAsJsonArray("data").get(0).getAsJsonObject().getAsJsonArray("content").get(0).getAsJsonObject().getAsJsonObject("text").get("value").getAsString();

        return res;
    }
}
