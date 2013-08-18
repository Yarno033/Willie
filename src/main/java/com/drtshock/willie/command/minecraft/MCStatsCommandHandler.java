package com.drtshock.willie.command.minecraft;

import com.drtshock.willie.Willie;
import com.drtshock.willie.command.CommandHandler;
import com.drtshock.willie.util.Tools;
import com.drtshock.willie.util.WebHelper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.pircbotx.Channel;
import org.pircbotx.Colors;
import org.pircbotx.User;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MCStatsCommandHandler implements CommandHandler {

    private static final Logger LOG = Logger.getLogger(MCStatsCommandHandler.class.getName());

    @Override
    public void handle(Willie bot, Channel channel, User sender, String[] args) throws Exception {
        if (args.length != 1) {
            channel.sendMessage(Colors.RED + "Outputs MCStats informations with !stats <name>");
            return;
        }

        try {
            List<String> messages = new ArrayList<>();

            String mcStatsURL = "http://mcstats.org/plugin/";
            String pluginStatsURL = mcStatsURL + args[0];
            Document doc = parse(getPage(pluginStatsURL));
            PluginStats stats = PluginStats.get(doc);

            try {
                String globalStatsJsonString = getPage("http://api.mcstats.org/1.0/" + stats.name + "/graph/Global+Statistics");
                stats.getMax(new JsonParser().parse(globalStatsJsonString).getAsJsonObject());
            } catch (Exception e) {
                LOG.log(Level.WARNING, e.getMessage(), e);
                stats.serversMax = "?";
                stats.playersMax = "?";
            }

            messages.add(Colors.BOLD + "MCStats" + Colors.NORMAL + " informations for plugin " + Colors.DARK_GREEN + stats.name +
                         Colors.NORMAL + " - " + WebHelper.shortenURL(pluginStatsURL));
            messages.add("Rank: " + Colors.BOLD + stats.rank + Colors.NORMAL + " (" + colorizeDiff(stats.rankDiff, true) +
                         ") | Servers: " + Colors.BOLD + stats.servers + Colors.NORMAL + " (" + colorizeDiff(stats.serversDiff, false) +
                         ", " + Colors.DARK_BLUE + stats.serversMax + Colors.NORMAL +
                         ") | Players: " + Colors.BOLD + stats.players + Colors.NORMAL + " (" + colorizeDiff(stats.playersDiff, false) +
                         ", " + Colors.DARK_BLUE + stats.playersMax + Colors.NORMAL +
                         ")");

            String authModeJsonString = getPage("http://api.mcstats.org/1.0/" + stats.name + "/graph/Auth+Mode");
            if (!authModeJsonString.contains("NO DATA")) {
                JsonObject authModeJson = new JsonParser().parse(authModeJsonString).getAsJsonObject();
                JsonArray array = authModeJson.getAsJsonArray("data");

                String offlineModeAmount = array.get(0).getAsJsonArray().get(0).getAsString().substring(9);
                offlineModeAmount = offlineModeAmount.substring(0, offlineModeAmount.length() - 1);
                String offlineModePercentage = array.get(0).getAsJsonArray().get(1).getAsString();

                String onlineModeAmount = array.get(1).getAsJsonArray().get(0).getAsString().substring(8);
                onlineModeAmount = onlineModeAmount.substring(0, onlineModeAmount.length() - 1);
                String onlineModePercentage = array.get(1).getAsJsonArray().get(1).getAsString();

                double left = Double.parseDouble(onlineModePercentage);
                double right = Double.parseDouble(offlineModePercentage);

                messages.add("Auth: " + Tools.asciiBar(left, Colors.DARK_GREEN, right, Colors.RED, 20, '█', '|', Colors.DARK_GRAY) +
                             " | " + Colors.DARK_GREEN + onlineModePercentage + "% (" + onlineModeAmount + ")" + Colors.NORMAL +
                             " - " + Colors.RED + offlineModePercentage + "% (" + offlineModeAmount + ")");
            } else {
                messages.add("Sorry, no auth information :-(");
            }

            for (String msg : messages) {
                channel.sendMessage(msg);
            }
        } catch (FileNotFoundException | MalformedURLException | IndexOutOfBoundsException | NumberFormatException e) {
            LOG.log(Level.INFO, "Plugin could not be found.", e);
            channel.sendMessage(Colors.RED + "Plugin could not be found.");
        } catch (IOException e) {
            channel.sendMessage(Colors.RED + "Failed: " + e.getMessage());
            throw e; // Gist
        }
    }

    private String getPage(String urlString) throws IOException {
        URL url = new URL(urlString);

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setUseCaches(false);

        BufferedReader input = new BufferedReader(new InputStreamReader(connection.getInputStream()));

        StringBuilder buffer = new StringBuilder();
        String line;

        while ((line = input.readLine()) != null) {
            buffer.append(line);
            buffer.append('\n');
        }

        String page = buffer.toString();

        input.close();

        return page;
    }

    private Document parse(String page) {
        return Jsoup.parse(page);
    }

    private static class PluginStats {

        public String name;
        public String rank;
        public String rankDiff;
        public String servers;
        public String serversDiff;
        public String serversMax;
        public String players;
        public String playersDiff;
        public String playersMax;

        private PluginStats() {
        }

        public void getMax(JsonObject json) {
            JsonObject data = json.getAsJsonObject("data");
            JsonArray players = data.getAsJsonArray("Players");
            Iterator<JsonElement> itPlayers = players.iterator();
            long maxPlayers = 0;
            while (itPlayers.hasNext()) {
                long amountPlayers = itPlayers.next().getAsJsonArray().get(1).getAsLong();
                if (amountPlayers > maxPlayers) {
                    maxPlayers = amountPlayers;
                }
            }
            this.playersMax = Long.toString(maxPlayers);
            JsonArray servers = data.getAsJsonArray("Servers");
            Iterator<JsonElement> itServers = servers.iterator();
            long maxServers = 0;
            while (itServers.hasNext()) {
                long amountServers = itServers.next().getAsJsonArray().get(1).getAsLong();
                if (amountServers > maxServers) {
                    maxServers = amountServers;
                }
            }
            this.serversMax = Long.toString(maxServers);
        }

        public static PluginStats get(Document doc) {
            PluginStats res = new PluginStats();
            res.name = doc.getElementsByClass("open").get(0).getElementsByTag("strong").get(0).ownText().trim();

            Element statBoxes = doc.getElementsByClass("stat-boxes").get(0);

            Element rankLi = statBoxes.child(0);
            Element serversLi = statBoxes.child(1);
            Element playersLi = statBoxes.child(2);

            Element rankLiStrong = rankLi.getElementsByClass("right").get(0).getElementsByTag("strong").get(0);
            if (rankLiStrong.children().size() == 0) {
                res.rank = rankLiStrong.ownText().trim();
            } else {
                res.rank = rankLiStrong.child(0).ownText().trim();
            }
            res.rankDiff = rankLi.getElementsByClass("left").get(0).ownText().trim();
            res.rankDiff = res.rankDiff.replace("&plusmn;", "±");

            Element serversLiStrong = serversLi.getElementsByClass("right").get(0).getElementsByTag("strong").get(0);
            if (serversLiStrong.children().size() == 0) {
                res.servers = serversLiStrong.ownText().trim();
            } else {
                res.servers = serversLiStrong.child(0).ownText().trim();
            }
            res.serversDiff = serversLi.getElementsByClass("left").get(0).ownText().trim();
            res.serversDiff = res.serversDiff.replace("&plusmn;", "±");

            Element playersLiStrong = playersLi.getElementsByClass("right").get(0).getElementsByTag("strong").get(0);
            if (playersLiStrong.children().size() == 0) {
                res.players = playersLiStrong.ownText().trim();
            } else {
                res.players = playersLiStrong.child(0).ownText().trim();
            }
            res.playersDiff = playersLi.getElementsByClass("left").get(0).ownText().trim();
            res.playersDiff = res.playersDiff.replace("&plusmn;", "±");

            return res;
        }
    }

    private String colorizeDiff(String diff, boolean reverse) {
        if (!reverse && diff.contains("+") || reverse && diff.contains("-")) {
            return Colors.BOLD + Colors.DARK_GREEN + diff + Colors.NORMAL;
        } else if (!reverse && diff.contains("-") || reverse && diff.contains("+")) {
            return Colors.BOLD + Colors.RED + diff + Colors.NORMAL;
        } else {
            return Colors.BOLD + Colors.DARK_GRAY + diff + Colors.NORMAL;
        }
    }

}