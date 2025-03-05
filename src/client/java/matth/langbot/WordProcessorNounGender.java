package matth.langbot;

import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class WordProcessorNounGender implements WordProcessor {
    public enum Identifiers {
        NOUN_MASCULINE("M-", Formatting.BLUE, "langbot.text.noun.masculine"),
        NOUN_FEMININE("F-", Formatting.RED, "langbot.text.noun.feminine"),
        NOUN_NEUTRAL("N-", Formatting.YELLOW, "langbot.text.noun.neutral");

        private final String value;
        private final Formatting colour;
        private final String descriptionLangCode;

        Identifiers(String s, Formatting colour, String descriptionLangCode) {
            this.value = s;
            this.colour = colour;
            this.descriptionLangCode = descriptionLangCode;
        }
    }

    @Override
    public MutableText processWord(MutableText word) {
        for (Identifiers identifier: Identifiers.values()) {
            if (word.getString().contains(identifier.value)) {
                // Remove identifier, and style with colour.
                return Text.literal(
                        word.getString().replaceFirst(identifier.value, ""))
                        .styled(style -> style
                                .withColor(identifier.colour)
                                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.translatable(identifier.descriptionLangCode)))
                        );
            }
        }

        // Unchanged.
        return word;
    }
}
