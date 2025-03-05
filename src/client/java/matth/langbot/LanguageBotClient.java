package matth.langbot;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
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
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class LanguageBotClient implements ClientModInitializer {
	public static final String MOD_ID = "languagebot";

	private static final MinecraftClient client = MinecraftClient.getInstance();

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

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
								.option(LabelOption.create(Text.translatable("langbot.text.openai.tokensused", openAiClient.getTokensUsed())))
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

	private void takeAndSendScreenshot(Runnable afterDone) {
		Consumer<Text> callback = s -> {
			client.options.hudHidden = false;

			Path imagePath = Path.of(client.runDirectory.toString(), "screenshots", "test.jpeg");
            try {
                openAiClient.addImageToSession(imagePath);
				afterDone.run();
            } catch (Exception e) {
                LOGGER.error(e.toString());
				LOGGER.error(Arrays.toString(e.getStackTrace()));
            }
        };

		// Wait a few ticks - the hud takes a bit of time to actually close.
		client.options.hudHidden = true;
		Framebuffer buffer = client.getFramebuffer();

		ClientTickDelay.waitTicks(1, () -> {
			ScreenshotRecorder.saveScreenshot(client.runDirectory, "test.jpeg", buffer, callback);
		});
	}

	private Runnable startCommand() {
		return () -> {
			try {
				openAiClient.startNewSession();
			} catch (Exception e) {
				LOGGER.error(e.getMessage());
			}
		};
	}

	private Runnable sendScreenshotCommand() {
		return () -> {
			takeAndSendScreenshot(() -> {
				try {
					this.respondToUser(openAiClient.runSession());
				} catch (Exception e) {
					LOGGER.error(e.getMessage());
				}
			});
		};
	}

	private Runnable sendPromptCommand(String prompt) {
		return () -> {
            try {
				openAiClient.addChatToSession(prompt);
                this.respondToUser(openAiClient.runSession());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
	}

	private void respondToUser(String message) {
		OutputProcessor processor =  new OutputProcessor();
		Objects.requireNonNull(client.player).sendMessage(processor.processIntoText(message), false);
	}

	private void registerCommands() {
		CommandRegistrationCallback.EVENT.register(((commandDispatcher, commandRegistryAccess, registrationEnvironment) -> {
			commandDispatcher.register(CommandManager
				.literal("lb")
				.then(CommandManager.literal("start").executes(context -> {
					this.startCommand().run();
					return Command.SINGLE_SUCCESS;
				})));
		}));

		CommandRegistrationCallback.EVENT.register(((commandDispatcher, commandRegistryAccess, registrationEnvironment) -> {
			commandDispatcher.register(CommandManager
				.literal("lb")
				.then(CommandManager.literal("picture").executes(context -> {
					this.sendScreenshotCommand().run();
					return Command.SINGLE_SUCCESS;
				})));
		}));

		CommandRegistrationCallback.EVENT.register(((commandDispatcher, commandRegistryAccess, registrationEnvironment) -> {
			commandDispatcher.register(CommandManager
					.literal("lb")
					.then(CommandManager.literal("prompt").then(
							CommandManager.argument("prompt", StringArgumentType.greedyString()).executes(ctx -> {
								String prompt = StringArgumentType.getString(ctx, "prompt");
								this.sendPromptCommand(prompt).run();
								return Command.SINGLE_SUCCESS;
							}
						))
					)
			);
		}));
	}
}