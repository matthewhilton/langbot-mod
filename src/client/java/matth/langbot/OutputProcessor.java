package matth.langbot;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.util.Arrays;
import java.util.List;

public class OutputProcessor {
    private static List<WordProcessor> processors = List.of(
            new WordProcessorNounGender(),
            new WordProcessorClickToTranslate()
    );

    public Text processIntoText(String text) {
        // Split into words.
        return Arrays.stream(text.split(" ")).map(descriptionWord -> {
            // Apply all word processors on each word.
            MutableText temp = Text.literal(descriptionWord);
            for (WordProcessor processor: processors) {
                temp = processor.processWord(temp);
            }
            return temp;
        }).reduce(Text.empty(), (prev, next) -> prev.append(" ").append(next));

    }
}
