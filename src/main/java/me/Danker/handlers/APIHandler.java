package me.Danker.handlers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.Danker.DankersSkyblockMod;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class APIHandler {
	public static JsonObject getResponse(String urlString, boolean hasError) {
		EntityPlayer player = Minecraft.getMinecraft().thePlayer;
		
		try {
			URL url = new URL(urlString);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setRequestProperty("User-Agent", "Dsm/1.0");
			
			if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
				BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
				String input;
				StringBuilder response = new StringBuilder();
				
				while ((input = in.readLine()) != null) {
					response.append(input);
				}
				in.close();
				
				Gson gson = new Gson();

				return gson.fromJson(response.toString(), JsonObject.class);
			} else {
				if (hasError) {
					InputStream errorStream = conn.getErrorStream();
					try (Scanner scanner = new Scanner(errorStream)) {
						scanner.useDelimiter("\\Z");
						String error = scanner.next();
						
						Gson gson = new Gson();
						return gson.fromJson(error, JsonObject.class);
					}
				} else if (urlString.startsWith("https://api.mojang.com/users/profiles/minecraft/") && conn.getResponseCode() == 204) {
					player.addChatMessage(new ChatComponentText(DankersSkyblockMod.ERROR_COLOUR + "Failed with reason: Player does not exist."));
				} else {
					player.addChatMessage(new ChatComponentText(DankersSkyblockMod.ERROR_COLOUR + "Request failed. HTTP Error Code: " + conn.getResponseCode()));
				}
			}
		} catch (IOException ex) {
			player.addChatMessage(new ChatComponentText(DankersSkyblockMod.ERROR_COLOUR + "An error has occured. See logs for more details."));
			ex.printStackTrace();
		}

		return new JsonObject();
	}

	public static JsonObject getResponsePOST(String urlString, JsonObject body, boolean hasError) throws IOException {
		EntityPlayer player = Minecraft.getMinecraft().thePlayer;

		CloseableHttpClient httpClient = HttpClientBuilder.create().build();

		try {
			HttpPost req = new HttpPost(urlString);
			StringEntity params = new StringEntity(body.toString());
			req.addHeader("content-type", "application/json");
			req.setEntity(params);
			HttpResponse response = httpClient.execute(req);

			return new Gson().fromJson(EntityUtils.toString(response.getEntity(), "UTF-8"), JsonObject.class);
		} catch (Exception ex) {
			player.addChatMessage(new ChatComponentText(DankersSkyblockMod.ERROR_COLOUR + "An error has occured. See logs for more details."));
			ex.printStackTrace();
		} finally {
			httpClient.close();
		}

		return new JsonObject();
	}
	
	// Only used for UUID => Username
	public static JsonArray getArrayResponse(String urlString) {
		EntityPlayer player = Minecraft.getMinecraft().thePlayer;
		
		try {
			URL url = new URL(urlString);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			
			if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
				BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
				String input;
				StringBuilder response = new StringBuilder();
				
				while ((input = in.readLine()) != null) {
					response.append(input);
				}
				in.close();
				
				Gson gson = new Gson();

				return gson.fromJson(response.toString(), JsonArray.class);
			} else {
				player.addChatMessage(new ChatComponentText(DankersSkyblockMod.ERROR_COLOUR + "Request failed. HTTP Error Code: " + conn.getResponseCode()));
			}
		} catch (IOException ex) {
			player.addChatMessage(new ChatComponentText(DankersSkyblockMod.ERROR_COLOUR + "An error has occured. See logs for more details."));
			ex.printStackTrace();
		}

		return new JsonArray();
	}
	
	public static String getUUID(String username) {
		JsonObject uuidResponse = getResponse("https://api.mojang.com/users/profiles/minecraft/" + username, false);
		return uuidResponse.get("id").getAsString();
	}
	
	public static String getLatestProfileID(String UUID, String key) {
		EntityPlayer player = Minecraft.getMinecraft().thePlayer;
		
		// Get profiles
		System.out.println("Fetching profiles...");
		
		JsonObject profilesResponse = getResponse("https://api.hypixel.net/skyblock/profiles?uuid=" + UUID + "&key=" + key, true);
		if (!profilesResponse.get("success").getAsBoolean()) {
			String reason = profilesResponse.get("cause").getAsString();
			player.addChatMessage(new ChatComponentText(DankersSkyblockMod.ERROR_COLOUR + "Failed with reason: " + reason));
			return null;
		}
		if (profilesResponse.get("profiles").isJsonNull()) {
			player.addChatMessage(new ChatComponentText(DankersSkyblockMod.ERROR_COLOUR + "This player doesn't appear to have played SkyBlock."));
			return null;
		}
		
		// Loop through profiles to find latest
		System.out.println("Looping through profiles...");
		String latestProfile = "";
		long latestSave = 0;
		JsonArray profilesArray = profilesResponse.get("profiles").getAsJsonArray();
		
		for (JsonElement profile : profilesArray) {
			JsonObject profileJSON = profile.getAsJsonObject();
			long profileLastSave = 1;
			if (profileJSON.get("members").getAsJsonObject().get(UUID).getAsJsonObject().has("last_save")) {
				profileLastSave = profileJSON.get("members").getAsJsonObject().get(UUID).getAsJsonObject().get("last_save").getAsLong();
			}
			
			if (profileLastSave > latestSave) {
				latestProfile = profileJSON.get("profile_id").getAsString();
				latestSave = profileLastSave;
			}
		}
		
		return latestProfile;
	}
}
