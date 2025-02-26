package matth.langbot;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public interface AiClient {
    public String executePrompt(String prompt, Optional<Path> imagePath) throws IOException;
    public boolean isConnectionOk();
    public void requestConnectionCheck();
}
