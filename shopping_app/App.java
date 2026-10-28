import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

class Item {
    int id;
    String name;
    String category;
    int price;
    boolean purchased;

    Item(int id, String name, String category, int price, boolean purchased) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price;
        this.purchased = purchased;
    }
}

public class App {
    static List<Item> items = new ArrayList<>();
    static int nextId = 1;
    static final Path CSV = Path.of("shopping.csv");

    public static void main(String[] args) throws Exception {
        load(); // ★ テーマ1：起動時にCSVを読み込む
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", App::handle);
        server.start();
        System.out.println("起動: http://localhost:8080  (停止: Ctrl+C)");
    }

    static void handle(HttpExchange ex) throws java.io.IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        try {
            // ★ テーマ2：JSON API
            if (path.equals("/api/items") && method.equals("GET")) {
                send(ex, 200, "application/json", makeJson());
                return;
            }
            if (path.equals("/") && method.equals("GET")) {
                send(ex, 200, "text/html; charset=UTF-8", makeHtml());
                return;
            }
            if (method.equals("POST")) {
                java.util.Map<String, String> form = readForm(ex);
                if (path.equals("/add")) {
                    String name = form.getOrDefault("name", "").trim();
                    String category = form.getOrDefault("category", "その他");
                    if (!category.equals("食品") && !category.equals("日用品")) category = "その他";
                    if (!name.isEmpty()) {
                        int price = Math.max(0, Integer.parseInt(form.getOrDefault("price", "0")));
                        // CSVのカンマ区切りを壊さないように、半角カンマは全角に置換
                        name = name.replace(',', '，').replace('\n', ' ').replace('\r', ' ');
                        items.add(new Item(nextId++, name, category, price, false));
                        save();
                    }
                } else if (path.equals("/toggle")) {
                    int id = Integer.parseInt(form.getOrDefault("id", "-1"));
                    for (Item item : items) {
                        if (item.id == id) {
                            item.purchased = !item.purchased;
                            save();
                            break;
                        }
                    }
                } else if (path.equals("/delete")) {
                    int id = Integer.parseInt(form.getOrDefault("id", "-1"));
                    items.removeIf(item -> item.id == id);
                    save();
                } else if (path.equals("/delete-completed")) {
                    items.removeIf(item -> item.purchased);
                    save();
                } else {
                    send(ex, 404, "text/plain; charset=UTF-8", "ページが見つかりません");
                    return;
                }
                ex.getResponseHeaders().set("Location", "/");
                ex.sendResponseHeaders(303, -1);
                ex.close();
                return;
            }
            send(ex, 404, "text/plain; charset=UTF-8", "ページが見つかりません");
        } catch (NumberFormatException e) {
            send(ex, 400, "text/plain; charset=UTF-8", "価格や番号は整数で入力してください");
        } catch (Exception e) {
            e.printStackTrace();
            send(ex, 500, "text/plain; charset=UTF-8", "処理中にエラーが起きました");
        }
    }

    static java.util.Map<String, String> readForm(HttpExchange ex) throws java.io.IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        java.util.Map<String, String> values = new java.util.HashMap<>();
        for (String part : body.split("&")) {
            String[] pair = part.split("=", 2);
            String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            String value = pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
            values.put(key, value);
        }
        return values;
    }

    static String makeHtml() {
        int remaining = 0;
        int remainingCost = 0;
        int purchasedCost = 0;
        for (Item item : items) {
            if (item.purchased) purchasedCost += item.price;
            else {
                remaining++;
                remainingCost += item.price;
            }
        }
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang='ja'><head><meta charset='UTF-8'>")
            .append("<meta name='viewport' content='width=device-width,initial-scale=1'>")
            .append("<title>買い物リスト</title><style>")
            .append("body{font-family:sans-serif;background:#f4f7fb;max-width:700px;margin:32px auto;padding:0 16px;color:#222}")
            .append("h1{color:#234} .panel{background:white;border-radius:12px;padding:18px;margin:14px 0;box-shadow:0 2px 8px #ddd}")
            .append("input,select,button{padding:9px;margin:3px} button{cursor:pointer}li{padding:10px 0;border-bottom:1px solid #eee}")
            .append("form.inline{display:inline} .done{color:#888;text-decoration:line-through} .muted{color:#666;font-size:14px}")
            .append("</style></head><body><h1>🛒 買い物リスト</h1>")
            .append("<div class='panel'><h2>商品を追加</h2><form action='/add' method='post'>")
            .append("<input name='name' placeholder='商品名（例：牛乳）' required>")
            .append("<select name='category'><option>食品</option><option>日用品</option><option>その他</option></select>")
            .append("<input type='number' name='price' value='0' min='0' required style='width:100px'>円 ")
            .append("<button type='submit'>追加</button></form></div>")
            .append("<div class='panel'><h2>買うもの</h2>")
            .append("<p>あと <strong>").append(remaining).append("件</strong> / 未購入の予定額: ")
            .append(remainingCost).append("円 / 購入済み金額: ").append(purchasedCost).append("円</p>");
        if (items.isEmpty()) html.append("<p>買うものは、いまゼロです。</p>");
        else if (remaining == 0) html.append("<p>🎉 ぜんぶ買えました！</p>");
        html.append("<ol>");
        for (Item item : items) {
            html.append("<li><span class='").append(item.purchased ? "done" : "")
                .append("'>").append(escapeHtml(item.name)).append("</span> <span class='muted'>[")
                .append(escapeHtml(item.category)).append("] ").append(item.price).append("円</span> ");
            html.append("<form class='inline' action='/toggle' method='post'><input type='hidden' name='id' value='")
                .append(item.id).append("'><button>").append(item.purchased ? "未購入に戻す" : "購入済み")
                .append("</button></form>");
            html.append("<form class='inline' action='/delete' method='post'><input type='hidden' name='id' value='")
                .append(item.id).append("'><button>削除</button></form></li>");
        }
        html.append("</ol><form action='/delete-completed' method='post'><button>購入済みを一括削除</button></form>")
            .append("<p class='muted'>JSON API: <a href='/api/items'>/api/items</a></p></div></body></html>");
        return html.toString();
    }

    static String makeJson() {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            Item item = items.get(i);
            if (i > 0) json.append(",");
            json.append("{\"id\":").append(item.id)
                .append(",\"name\":\"").append(escapeJson(item.name))
                .append("\",\"category\":\"").append(escapeJson(item.category))
                .append("\",\"price\":").append(item.price)
                .append(",\"purchased\":").append(item.purchased).append("}");
        }
        return json.append("]").toString();
    }

    static String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    static void send(HttpExchange ex, int status, String type, String text) throws java.io.IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    static void save() throws java.io.IOException {
        List<String> lines = new ArrayList<>();
        for (Item item : items) {
            lines.add(item.id + "," + (item.purchased ? 1 : 0) + "," + item.name + "," + item.category + "," + item.price);
        }
        Files.write(CSV, lines, StandardCharsets.UTF_8); // ★ テーマ1：CSV保存
    }

    static void load() throws java.io.IOException {
        if (!Files.exists(CSV)) return;
        for (String line : Files.readAllLines(CSV, StandardCharsets.UTF_8)) {
            String[] f = line.split(",", 5);
            if (f.length != 5) continue;
            int id = Integer.parseInt(f[0]);
            items.add(new Item(id, f[2], f[3], Integer.parseInt(f[4]), f[1].equals("1")));
            nextId = Math.max(nextId, id + 1);
        }
    }
}
