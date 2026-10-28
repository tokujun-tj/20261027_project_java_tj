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

    // ★変更: 画面のHTML/CSSを、明るく見やすいカード型デザインに変更
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
            // ★変更: ミントグリーンと水色の背景、カード、丸い部品、ホバー効果、レスポンシブ対応
            .append(":root{--mint:#dff8ee;--sky:#dff3ff;--green:#38c982;--green-dark:#20a968;--red:#ffe1e1;--ink:#24424b;--muted:#6c858b}")
            .append("*{box-sizing:border-box}body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','Noto Sans JP',sans-serif;background:linear-gradient(135deg,var(--mint),var(--sky));max-width:820px;margin:0 auto;padding:32px 16px;color:var(--ink);min-height:100vh}")
            .append("h1{color:var(--ink);text-align:center;font-size:clamp(28px,6vw,44px);margin:12px 0 24px}h2{color:var(--ink);margin-top:0} .panel{background:rgba(255,255,255,.92);border:1px solid rgba(255,255,255,.8);border-radius:24px;padding:24px;margin:16px 0;box-shadow:0 12px 30px rgba(72,137,148,.14)}")
            .append("input,select,button{padding:11px 13px;margin:4px;border-radius:12px;font-size:15px}input,select{border:2px solid #d7edf0;background:#fff;color:var(--ink)}input:focus,select:focus{outline:none;border-color:#73d8c1;box-shadow:0 0 0 4px rgba(115,216,193,.18)}button{cursor:pointer;border:0;background:#e8f7ff;color:#267088;font-weight:700;transition:transform .2s,box-shadow .2s,background .2s}button:hover{transform:translateY(-2px);box-shadow:0 6px 14px rgba(36,66,75,.16)}")
            .append("li{display:flex;align-items:center;gap:10px;padding:16px;margin:10px 0;border:1px solid #e1f0f0;border-radius:16px;background:#fff;box-shadow:0 4px 12px rgba(72,137,148,.08);list-style-position:inside}li form{margin-left:auto}li form:last-child button{background:var(--red);color:#b84f5d}.panel>p:first-of-type{padding:16px;border-radius:16px;background:#f5fffb;font-size:18px;line-height:1.8}.panel>p:first-of-type strong{color:#168b68;font-size:25px}")
            .append("form.inline{display:inline}.done{color:#9aaeb2;text-decoration:line-through}.muted{color:var(--muted);font-size:14px}a{color:#267da0}.panel>ol{padding-left:0}.panel>form:last-of-type button{background:#fff0f0;color:#b84f5d}")
            .append("@media(max-width:600px){body{padding:20px 10px}.panel{padding:18px;border-radius:18px}input,select{width:calc(50% - 12px)}input[name='name']{width:100%}li{flex-wrap:wrap}li form{margin-left:0}.panel>p:first-of-type{font-size:15px}}")
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
