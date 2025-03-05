package matth.langbot;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;

public class WordProcessorClickToTranslate implements WordProcessor {
    @Override
    public MutableText processWord(MutableText word) {
        // TODO fix this hack and properly convert mutable text to plain text.
        String plaintext = word.getContent().toString().replaceFirst("literal\\{", "").replaceFirst("}", "");
        return word.styled(s -> s.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/lb prompt Meaning of:'" + plaintext)));
    }
}
