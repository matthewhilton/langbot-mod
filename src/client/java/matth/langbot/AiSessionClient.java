package matth.langbot;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public interface AiSessionClient {
    public void startNewSession();
    public void addImageToSession(Path imagePath) throws Exception;
    public void addChatToSession(String chat);
    public String runSession() throws Exception;
}
