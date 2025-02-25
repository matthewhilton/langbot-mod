package matth.langbot;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import org.apache.commons.lang3.StringUtils;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Objects;
import java.util.function.Consumer;

public class LanguageBotClient implements ClientModInitializer {
	public static final String MOD_ID = "languagebot";

	private static final MinecraftClient client = MinecraftClient.getInstance();

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	
	public static final AiClientHandler handler = new AiClientHandler(LOGGER, client);

	@Override
	public void onInitializeClient() {
		this.registerCommands();
		this.registerKeyBinds();
		ClientTickDelay.init();
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
		client.setScreen(new LangBotScreen(new LangBotGui(LOGGER, handler, this.getRunnableScreenshotAndTranslate())));
	}

	private Runnable getRunnableScreenshotAndTranslate() {
		return () -> {
			// HUD messes with the image describer.
			client.options.hudHidden = true;
			Framebuffer buffer = client.getFramebuffer();

			Consumer<Text> callback = s -> {
				client.options.hudHidden = false;
				Objects.requireNonNull(client.player).networkHandler.sendChatMessage("Processing image...");

				// Must be < 256 chars to send to client.
				String description = StringUtils.left(handler.convertScreenshotToText("test.jpeg"), 255);

				Objects.requireNonNull(client.player).networkHandler.sendChatMessage(description);
				Objects.requireNonNull(client.player).networkHandler.sendChatMessage("Translating into German");

				String translation = StringUtils.left(handler.translateDescriptionIntoLanguage("German", description), 255);
				Objects.requireNonNull(client.player).networkHandler.sendChatMessage(translation);
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