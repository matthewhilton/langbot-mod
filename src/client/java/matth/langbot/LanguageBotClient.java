package matth.langbot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import okhttp3.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class LanguageBotClient implements ClientModInitializer {
	public static final String MOD_ID = "languagebot";

	private static final MinecraftClient client = MinecraftClient.getInstance();

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private String convertScreenshotToText(String filename) {
		String screenshotPath = client.runDirectory.getAbsolutePath() + "\\screenshots\\" + filename;
		return this.callGenerateAndGetResponse("llava:7b",
				"Explain what is shown in this minecraft world in 1 sentence with a maximum of 7 words.",
						new String[]{ screenshotPath }
		);
	}

	private String translateDescriptionIntoLanguage(String language, String description) {
		return this.callGenerateAndGetResponse("llama3:8b", "Translate this into " + language + ", and do not say anything else: " + description);
	}

	private String callGenerateAndGetResponse(String model, String prompt) {
		return this.callGenerateAndGetResponse(model, prompt, new String[]{});
	}

	private String callGenerateAndGetResponse(String model, String prompt, String[] imagePaths) {
		OkHttpClient httpClient = new OkHttpClient.Builder()
				.connectTimeout(120, TimeUnit.SECONDS)
				.writeTimeout(120, TimeUnit.SECONDS)
				.readTimeout(120, TimeUnit.SECONDS)
				.build();

		JsonObject jsonObject = new JsonObject();
		jsonObject.addProperty("model", model);
		jsonObject.addProperty("prompt", prompt);
		jsonObject.addProperty("stream", false);

		if  (imagePaths.length >  0) {
			JsonArray images = new JsonArray();

			try {
				for (String imagePath: imagePaths) {
					byte[] imageBytes = Files.readAllBytes(Paths.get(imagePath));
					String base64Image = Base64.getEncoder().encodeToString(imageBytes);
					images.add(base64Image);
				}
			} catch (Exception e) {
				LOGGER.error(e.toString());
			}

			jsonObject.add("images", images);
		}

		Request request = new Request.Builder()
				.url("http://localhost:11434/api/generate")
				.post(RequestBody.create(MediaType.parse("text/json"), jsonObject.toString()))
				.build();

		// Call and get the response.
		try (Response response = httpClient.newCall(request).execute()) {
			if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

			JsonObject o = JsonParser.parseString(response.body() != null ? response.body().string() : "{}").getAsJsonObject();
			return o.get("response").toString();
		} catch (IOException e) {
            return "";
        }
    }

	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
		CommandRegistrationCallback.EVENT.register(((commandDispatcher, commandRegistryAccess, registrationEnvironment) -> {
			commandDispatcher.register(CommandManager.literal("test_command").executes(context -> {
				// HUD messes with the image describer.
				client.options.hudHidden = true;
				Framebuffer buffer = client.getFramebuffer();

				Consumer<Text> callback = s -> {
					client.options.hudHidden = false;
					Objects.requireNonNull(client.player).networkHandler.sendChatMessage("Processing image...");

					// Must be < 256 chars to send to client.
					String description = StringUtils.left(convertScreenshotToText("test.jpeg"), 255);

					Objects.requireNonNull(client.player).networkHandler.sendChatMessage(description);
					Objects.requireNonNull(client.player).networkHandler.sendChatMessage("Translating into German");

					String translation = StringUtils.left(translateDescriptionIntoLanguage("German", description), 255);
					Objects.requireNonNull(client.player).networkHandler.sendChatMessage(translation);
				};

				ScreenshotRecorder.saveScreenshot(client.runDirectory, "test.jpeg", buffer, callback);
				return 1;
			}));
		}));
	}
}