package matth.langbot;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

public interface WordProcessor {
    MutableText processWord(MutableText word);
}
