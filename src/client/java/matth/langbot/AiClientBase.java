package matth.langbot;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public interface AiClientBase {
    public boolean isConnectionOk();
    public void requestConnectionCheck();
}
