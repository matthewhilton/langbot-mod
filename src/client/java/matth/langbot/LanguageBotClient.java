package matth.langbot;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

public class LanguageBotClient implements ClientModInitializer {
	public static final String MOD_ID = "languagebot";

	private static final MinecraftClient client = MinecraftClient.getInstance();

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final AiClient openAiClient = new AIClientOpenAI(LOGGER);

	@Override
	public void onInitializeClient() {
		LOGGER.debug("Loading config");
		LangBotConfig.HANDLER.load();

		LOGGER.debug("Registering commands");
		this.registerCommands();

		LOGGER.debug("Registering key binds");
		this.registerKeyBinds();

		LOGGER.debug("Registering tick task scheduler");
		ClientTickDelay.init();

		openAiClient.requestConnectionCheck();
	}

	private void registerKeyBinds() {
		KeyBinding bind = KeyBindingHelper.registerKeyBinding(new KeyBinding(
				"langbot.key.opensettings",
				InputUtil.Type.KEYSYM,
				GLFW.GLFW_KEY_Y,
				"category.langbot"
		));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (bind.wasPressed()) {
				this.openGui();
			}
		});
	}

	private void openGui() {
		Screen screen = YetAnotherConfigLib.createBuilder()
				.title(Text.translatable("langbot.text.name"))
				.category(ConfigCategory.createBuilder()
						.name(Text.translatable("langbot.text.name"))
						.group(OptionGroup.createBuilder()
								.name(Text.translatable("langbot.text.openai"))
								.option(Option.<String>createBuilder()
										.name(Text.translatable("langbot.text.openai.apikey"))
										.description(OptionDescription.of(Text.translatable("langbot.text.openai.apikey.description")))
										.binding("", () -> LangBotConfig.HANDLER.instance().openAiKey, newVal -> LangBotConfig.HANDLER.instance().openAiKey = newVal)
										.controller(StringControllerBuilder::create)
										.build())
								.option(ButtonOption.createBuilder()
										.name(Text.translatable("langbot.text.connectioncheck"))
										.text(Text.literal(""))
										.action((s, option) -> {
											openAiClient.requestConnectionCheck();
										})
										.build())
								.option(LabelOption.createBuilder()
										// This is a hack to get updating labels. But it doesn't update the sidebar. TODO implement this properly (likely upstream lib modification).
										.state(StateManager.createInstant(Binding.generic(Text.literal(""), () -> openAiClient.isConnectionOk() ? Text.translatable("langbot.text.connectioncheck.ok").formatted(Formatting.GREEN) : Text.translatable("langbot.text.connectioncheck.error").formatted(Formatting.RED), v -> {})))
										.build())
								.build())
						.build())
				.build()
				.generateScreen(client.currentScreen);

		client.setScreen(screen);

		ScreenEvents.remove(screen).register(_s -> {
			LOGGER.info("Saving config");
			LangBotConfig.HANDLER.save();
		});
	}

	private Runnable getRunnableScreenshotAndTranslate() {
		return () -> {
			// HUD messes with the image describer.
			client.options.hudHidden = true;
			Framebuffer buffer = client.getFramebuffer();

			Consumer<Text> callback = s -> {
				client.options.hudHidden = false;
				client.player.sendMessage(Text.literal("Processing..."), false);

                try {
					Path imagePath = Path.of(client.runDirectory.toString(), "screenshots", "test.jpeg");
					String describePrompt = "describe the image in simple terms but don't mention minecraft or video games. max 1 sentence. max 10 words.";
					String translatePromptPrefix = "Translate to German do not say anything else:";
                    String description = openAiClient.executePrompt(describePrompt, Optional.of(imagePath));
                	client.player.sendMessage(Text.literal(description), false);

					String translation = openAiClient.executePrompt(translatePromptPrefix + description, Optional.empty());
					client.player.sendMessage(Text.literal(translation), false);
				} catch (IOException e) {
                    LOGGER.error(e.getMessage());
					// TODO this formatting is stripped.
					client.player.sendMessage(Text.translatable("langbot.error.unknown", e.getMessage().formatted(Formatting.RED)), false);
                }
			};

			ScreenshotRecorder.saveScreenshot(client.runDirectory, "test.jpeg", buffer, callback);
		};
	}

	private void registerCommands() {
		CommandRegistrationCallback.EVENT.register(((commandDispatcher, commandRegistryAccess, registrationEnvironment) -> {
			commandDispatcher.register(CommandManager.literal("langbot").executes(context -> {
				this.getRunnableScreenshotAndTranslate().run();
				return 1;
			}));
		}));
	}
}