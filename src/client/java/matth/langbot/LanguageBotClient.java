package matth.langbot;

import dev.isxander.yacl3.api.*;
import dev.isxander.yacl3.api.controller.BooleanControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
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

import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;

public class LanguageBotClient implements ClientModInitializer {
	public static final String MOD_ID = "languagebot";

	private static final MinecraftClient client = MinecraftClient.getInstance();

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	
	public static final AiClientHandler handler = new AiClientHandler(LOGGER, client);

	public static final AIClientOpenAI openAiClient = new AIClientOpenAI(LOGGER);

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
				"key.langbot.my_key",
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
				.title(Text.literal("Used for narration. Could be used to render a title in the future."))
				.category(ConfigCategory.createBuilder()
						.name(Text.literal("Name of the category"))
						.tooltip(Text.literal("This text will appear as a tooltip when you hover or focus the button with Tab. There is no need to add \n to wrap as YACL will do it for you."))
						.group(OptionGroup.createBuilder()
								.name(Text.literal("OpenAI Settings"))
								.description(OptionDescription.of(Text.literal("OpenAI Settings")))
								.option(Option.<String>createBuilder()
										.name(Text.literal("API Key"))
										.description(OptionDescription.of(Text.literal("Key used to authenticate with OpenAI.")))
										.binding("", () -> LangBotConfig.HANDLER.instance().openAiKey, newVal -> LangBotConfig.HANDLER.instance().openAiKey = newVal)
										.controller(StringControllerBuilder::create)
										.build())
								.option(ButtonOption.createBuilder()
										.name(Text.literal("Check connection status"))
										.description(OptionDescription.of(Text.literal("Check the API key can correctly communicate with OpenAI")))
										.text(Text.literal(""))
										.action((s, option) -> {
											openAiClient.requestConnectionCheck();
										})
										.build())
								.option(LabelOption.createBuilder()
										// This is a hack to get updating labels. But it doesn't update the sidebar. TODO implement this properly (likely upstream lib modification).
										.state(StateManager.createInstant(Binding.generic(Text.literal("default"), () -> openAiClient.isConnectionOk() ? Text.literal("Connection OK").formatted(Formatting.GREEN) : Text.literal("Connection ERROR").formatted(Formatting.RED), v -> {})))
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

				// Must be < 256 chars to send to client.
                try {
                    String description = StringUtils.left(openAiClient.describeImageWithPrompt(Path.of(client.runDirectory.toString(), "screenshots", "test.jpeg"), "describe the image in simple terms but don't mention minecraft or video games. max 1 sentence. max 10 words."), 255);
                	client.player.sendMessage(Text.literal(description), false);
				} catch (IOException e) {
                    LOGGER.error(e.getMessage());
                }

				/*Objects.requireNonNull(client.player).networkHandler.sendChatMessage(description);
				Objects.requireNonNull(client.player).networkHandler.sendChatMessage("Translating into German");

				String translation = StringUtils.left(handler.translateDescriptionIntoLanguage("German", description), 255);
				Objects.requireNonNull(client.player).networkHandler.sendChatMessage(translation);*/
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