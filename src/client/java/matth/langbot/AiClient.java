package matth.langbot;

import okhttp3.OkHttpClient;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

public interface AiClient {
    public String describeImageWithPrompt(Path imagePath, String prompt) throws IOException;
    public String translateIntoLanguage(String message, String language);
    public boolean isConnectionOk();
    public void requestConnectionCheck();

    default OkHttpClient getHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
    }
}
