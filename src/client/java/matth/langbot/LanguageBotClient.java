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
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class LanguageBotClient implements ClientModInitializer {
	public static final String MOD_ID = "languagebot";

	private static final MinecraftClient client = MinecraftClient.getInstance();

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final AIClientOpenAI openAiClient = new AIClientOpenAI(LOGGER);

	private String lastPromptResponse = "";

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
					String describePrompt = "Give one short A1 level German question about the image contents. Do not ask about where. Only give the question nothing else. Max 10 words.";
                    String description = openAiClient.executePrompt(describePrompt, Optional.of(imagePath));
					this.lastPromptResponse = description;
					client.player.sendMessage(this.makeWordsTranslatable(description), false);
				} catch (IOException e) {
                    LOGGER.error(e.getMessage());
					client.player.sendMessage(Text.translatable("langbot.error.unknown", e.getMessage()), false);
                }
			};

			ScreenshotRecorder.saveScreenshot(client.runDirectory, "test.jpeg", buffer, callback);
		};
	}

	private Runnable replyToPrompt(String reply, boolean includePrevious) {
		return () -> {
			try {
				client.player.sendMessage(Text.literal("Processing..."), false);
				String prompt = (includePrevious ? "Previous prompt: " + this.lastPromptResponse + ". Reply: " : "") + reply + ". Reply with only 1 sentence of max 12 words.";
				String output = openAiClient.executePrompt(prompt, Optional.empty());
				this.lastPromptResponse = output;
				client.player.sendMessage(this.makeWordsTranslatable(output), false);
			} catch (IOException e) {
				LOGGER.error(e.getMessage());
				client.player.sendMessage(Text.translatable("langbot.error.unknown", e.getMessage()), false);
			}
		};
	}

	private Text makeWordsTranslatable(String text) {
		// Split each word and add a hover event for each to find out the meaning.
		Pattern textPattern = Pattern.compile("[a-zA-Z]");
		return Arrays.stream(text.split(" ")).map(descriptionWord -> {
			// Return part plainly if it has no text (e.g. is symbols or numbers).
			if (!textPattern.matcher(descriptionWord).find()) {
				return Text.literal(descriptionWord)
					.styled(style -> style
						.withColor(Formatting.WHITE)
					);
			}

			// Else add hover.
			return Text.literal(descriptionWord)
				.styled(style -> style
					.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(String.format("Was bedeutet '%s'", descriptionWord))))
					.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, String.format("/lb prompt Give the definition in German in maximum 10 words of '%s'. Ignore any punctuation.", descriptionWord)))
					.withColor(Formatting.GREEN)
				);
		}).reduce(Text.empty(), (prev, next) -> prev.append(" ").append(next));
	}

	private void registerCommands() {
		CommandRegistrationCallback.EVENT.register(((commandDispatcher, commandRegistryAccess, registrationEnvironment) -> {
			commandDispatcher.register(CommandManager
				.literal("lb")
				.then(CommandManager.literal("start").executes(context -> {
					this.getRunnableScreenshotAndTranslate().run();
					return Command.SINGLE_SUCCESS;
				})));
		}));

		CommandRegistrationCallback.EVENT.register(((commandDispatcher, commandRegistryAccess, registrationEnvironment) -> {
			commandDispatcher.register(CommandManager
				.literal("lb")
				.then(CommandManager.literal("prompt")
						.then(CommandManager.argument("prompt", StringArgumentType.greedyString()).executes(context -> {
							this.replyToPrompt(StringArgumentType.getString(context, "prompt"), false).run();
							return Command.SINGLE_SUCCESS;
						}))
					));
		}));

		CommandRegistrationCallback.EVENT.register(((commandDispatcher, commandRegistryAccess, registrationEnvironment) -> {
			commandDispatcher.register(CommandManager
					.literal("lb")
					.then(CommandManager.literal("reply")
							.then(CommandManager.argument("reply", StringArgumentType.greedyString()).executes(context -> {
								this.replyToPrompt(StringArgumentType.getString(context, "reply"), true).run();
								return Command.SINGLE_SUCCESS;
							}))
					));
		}));
	}
}