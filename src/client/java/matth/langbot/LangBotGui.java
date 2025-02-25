package matth.langbot;

import io.github.cottonmc.cotton.gui.client.LightweightGuiDescription;
import io.github.cottonmc.cotton.gui.widget.*;
import io.github.cottonmc.cotton.gui.widget.data.Color;
import io.github.cottonmc.cotton.gui.widget.data.Insets;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;

public class LangBotGui extends LightweightGuiDescription {
    public LangBotGui(Logger logger, AiClientHandler handler, Runnable screenshotAndTranslate) {
        // Root.
        WGridPanel  root = new WGridPanel();
        setRootPanel(root);
        root.setSize(250, 150);
        root.setInsets(Insets.ROOT_PANEL);

        WLabel mainLabel = new WLabel(Text.translatable("langbot.text.name").formatted(Formatting.AQUA, Formatting.BOLD));
        mainLabel.setDrawShadows(true);
        root.add(mainLabel, 0, 0, 18, 1);

        // Model status and reload.
        for (int i = 0; i < AiClientHandler.requiredModels.length; i++) {
            String model = AiClientHandler.requiredModels[i];

            WDynamicLabel label = new WDynamicLabel(() -> switch (handler.getModelStatus(model)) {
                case Result.Ok<String> result -> I18n.translate("langbot.text.modelstatus.ok", model);
                case Result.Error<String> result -> I18n.translate("langbot.text.modelstatus.error", model);
                case Result.Loading<String> result -> I18n.translate("langbot.text.modelstatus.loading", model);
            });
            root.add(label, 0, i + 1);
        }
        WButton recheckModelsButton = new WButton(Text.translatable("langbot.text.recheckmodels"));
        root.add(recheckModelsButton, 0, 4, 18, 1);
        recheckModelsButton.setOnClick(handler::updateAvailableModels);
        handler.updateAvailableModels();

        // Run.
        WButton runButton = new WButton(Text.translatable("langbot.text.runscreenshotandtranslate"));
        root.add(runButton, 0, 5, 18, 1);
        runButton.setOnClick(() -> {
            // Close screen and wait a few ticks for it to actually close.
            MinecraftClient.getInstance().player.closeScreen();
            ClientTickDelay.waitTicks(10, () -> {
                screenshotAndTranslate.run();
            });
        });

        root.validate(this);
    }
}
