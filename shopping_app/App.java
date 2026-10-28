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
            // ★変更: 商品IDを指定した編集画面を表示
            if (path.equals("/edit") && method.equals("GET")) {
                int id = Integer.parseInt(queryValue(ex, "id"));
                Item item = findItem(id);
                if (item == null) {
                    send(ex, 404, "text/plain; charset=UTF-8", "商品が見つかりません");
                    return;
                }
                send(ex, 200, "text/html; charset=UTF-8", makeEditHtml(item.id, item.name,
                        item.category, String.valueOf(item.price), item.purchased, ""));
                return;
            }
            if (method.equals("POST")) {
                java.util.Map<String, String> form = readForm(ex);
                if (path.equals("/add")) {
                    String name = form.getOrDefault("name", "").trim();
                    String category = form.getOrDefault("category", "その他");
                    if (!category.equals("食品") && !category.equals("日用品")) category = "その他";
                    String rawPrice = form.getOrDefault("price", "").trim();
                    // ★変更: Java側でも、1円以上の整数だけを受け付ける
                    if (!isValidPrice(rawPrice)) {
                        String error = rawPrice.isEmpty()
                                ? "金額を入力してください"
                                : "金額は1円以上の整数で入力してください";
                        send(ex, 200, "text/html; charset=UTF-8",
                                makeHtml(name, category, rawPrice, error));
                        return;
                    }
                    if (!name.isEmpty()) {
                        int price = Integer.parseInt(rawPrice);
                        // CSVのカンマ区切りを壊さないように、半角カンマは全角に置換
                        name = name.replace(',', '，').replace('\n', ' ').replace('\r', ' ');
                        items.add(new Item(nextId++, name, category, price, false));
                        save();
                    }
                // ★変更: 商品IDごとに編集内容を検証してから更新
                } else if (path.equals("/update")) {
                    int id = Integer.parseInt(form.getOrDefault("id", "-1"));
                    Item item = findItem(id);
                    if (item == null) {
                        send(ex, 404, "text/plain; charset=UTF-8", "商品が見つかりません");
                        return;
                    }
                    String name = form.getOrDefault("name", "").trim();
                    String category = normalizeCategory(form.getOrDefault("category", "その他"));
                    String rawPrice = form.getOrDefault("price", "").trim();
                    String error = "";
                    if (name.isEmpty()) error = "商品名を入力してください";
                    else if (!isValidPrice(rawPrice)) {
                        error = rawPrice.isEmpty()
                                ? "金額を入力してください"
                                : "金額は1円以上の整数で入力してください";
                    }
                    if (!error.isEmpty()) {
                        send(ex, 200, "text/html; charset=UTF-8", makeEditHtml(id, name,
                                category, rawPrice, item.purchased, error));
                        return;
                    }
                    name = name.replace(',', '，').replace('\n', ' ').replace('\r', ' ');
                    item.name = name;
                    item.category = category;
                    item.price = Integer.parseInt(rawPrice);
                    save();
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

    // ★変更: 編集画面のGETクエリから商品IDを安全に読み取る
    static String queryValue(HttpExchange ex, String wantedKey) {
        String query = ex.getRequestURI().getRawQuery();
        if (query == null) return "";
        for (String part : query.split("&")) {
            String[] pair = part.split("=", 2);
            String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            if (key.equals(wantedKey)) {
                return pair.length == 2 ? URLDecoder.decode(pair[1], StandardCharsets.UTF_8) : "";
            }
        }
        return "";
    }

    // ★変更: 指定されたIDの商品だけを取得
    static Item findItem(int id) {
        for (Item item : items) {
            if (item.id == id) return item;
        }
        return null;
    }

    // ★変更: カテゴリの選択肢を既存仕様の3種類に限定
    static String normalizeCategory(String category) {
        if (category.equals("食品") || category.equals("日用品") || category.equals("その他")) {
            return category;
        }
        return "その他";
    }

    // ★変更: 空欄・文字列・小数・マイナス・0円・整数の範囲外を拒否
    static boolean isValidPrice(String rawPrice) {
        if (rawPrice == null || !rawPrice.matches("[0-9]+")) return false;
        try {
            return Integer.parseInt(rawPrice) >= 1;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    // ★変更: 商品編集画面。エラー時は送信値をそのまま再表示
    static String makeEditHtml(int id, String name, String category, String price,
                               boolean purchased, String error) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang='ja'><head><meta charset='UTF-8'>")
            .append("<meta name='viewport' content='width=device-width,initial-scale=1'>")
            .append("<title>商品を編集</title><style>")
            .append(":root{--mint:#dff8ee;--sky:#dff3ff;--green:#38c982;--green-dark:#20a968;--red:#c9364f;--ink:#24424b;--muted:#6c858b}")
            .append("*{box-sizing:border-box}body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','Noto Sans JP',sans-serif;background:linear-gradient(135deg,var(--mint),var(--sky));max-width:700px;margin:0 auto;padding:32px 16px;color:var(--ink);min-height:100vh}.panel{background:rgba(255,255,255,.94);border-radius:24px;padding:24px;box-shadow:0 12px 30px rgba(72,137,148,.14)}")
            .append("h1{margin-top:0}label{display:block;margin:16px 0 6px;font-weight:700}.field{width:100%;padding:12px 14px;border:2px solid #d7edf0;border-radius:12px;background:#fff;color:var(--ink);font-size:16px}.field:focus{outline:none;border-color:#73d8c1;box-shadow:0 0 0 4px rgba(115,216,193,.18)}")
            .append("button,.cancel{display:inline-block;margin-top:20px;padding:12px 18px;border:0;border-radius:12px;font-weight:700;text-decoration:none;cursor:pointer}.save{background:var(--green);color:#fff}.save:hover{background:var(--green-dark)}.cancel{background:#e8f7ff;color:#267088;margin-left:8px}.error{min-height:24px;color:var(--red);font-weight:700}.note{color:var(--muted);font-size:13px}@media(max-width:600px){body{padding:20px 10px}.panel{padding:18px;border-radius:18px}.cancel,button{width:100%;margin-left:0}}")
            .append("</style></head><body><main class='panel'><h1>商品を編集</h1><p class='note'>商品ID: ").append(id)
            .append(purchased ? "（購入済み）" : "（未購入）").append("</p>")
            .append("<form action='/update' method='post' onsubmit='return validateEdit(this)'>")
            .append("<input type='hidden' name='id' value='").append(id).append("'>")
            .append("<label for='name'>商品名</label><input class='field' id='name' name='name' required value='")
            .append(escapeHtml(name)).append("'>")
            .append("<label for='category'>カテゴリ</label><select class='field' id='category' name='category'>")
            .append("<option ").append(category.equals("食品") ? "selected" : "").append(">食品</option>")
            .append("<option ").append(category.equals("日用品") ? "selected" : "").append(">日用品</option>")
            .append("<option ").append(category.equals("その他") ? "selected" : "").append(">その他</option></select>")
            .append("<label for='price'>予定価格（円）</label><input class='field' id='price' name='price' type='number' min='1' step='1' required value='")
            .append(escapeHtml(price)).append("' oninput='clearEditError()'>")
            .append("<p class='error' id='edit-error' role='alert'>").append(escapeHtml(error)).append("</p>")
            .append("<button class='save' type='submit'>保存する</button><a class='cancel' href='/'>キャンセル</a></form>")
            .append("<script>function validateEdit(form){var name=form.elements.name.value.trim();var value=form.elements.price.value.trim();var error=document.getElementById('edit-error');if(name===''){error.textContent='商品名を入力してください';form.elements.name.focus();return false;}if(!/^[0-9]+$/.test(value)||Number(value)<1||!Number.isSafeInteger(Number(value))){error.textContent=value===''?'金額を入力してください':'金額は1円以上の整数で入力してください';form.elements.price.focus();return false;}error.textContent='';return true;}function clearEditError(){var error=document.getElementById('edit-error');if(error)error.textContent='';}</script></main></body></html>");
        return html.toString();
    }

    // ★変更: 画面のHTML/CSSを、明るく見やすいカード型デザインに変更
    static String makeHtml() {
        return makeHtml("", "その他", "", "");
    }

    // ★変更: エラー時に入力済みの商品名・カテゴリ・金額を画面へ戻す
    static String makeHtml(String formName, String formCategory, String formPrice, String priceError) {
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
            .append(".price-error{min-height:24px;margin:8px 4px;color:#c9364f;font-weight:700}")
            .append("@media(max-width:600px){body{padding:20px 10px}.panel{padding:18px;border-radius:18px}input,select{width:calc(50% - 12px)}input[name='name']{width:100%}li{flex-wrap:wrap}li form{margin-left:0}.panel>p:first-of-type{font-size:15px}}")
            .append("</style></head><body><h1>🛒 買い物リスト</h1>")
            .append("<div class='panel'><h2>商品を追加</h2><form action='/add' method='post' onsubmit='return validatePrice(this)'>")
            .append("<input name='name' placeholder='商品名（例：牛乳）' value='").append(escapeHtml(formName)).append("' required>")
            .append("<select name='category'>")
            .append("<option ").append(formCategory.equals("食品") ? "selected" : "").append(">食品</option>")
            .append("<option ").append(formCategory.equals("日用品") ? "selected" : "").append(">日用品</option>")
            .append("<option ").append(formCategory.equals("その他") ? "selected" : "").append(">その他</option></select>")
            .append("<input type='number' name='price' value='").append(escapeHtml(formPrice)).append("' min='1' step='1' required style='width:100px' oninput='clearPriceError()'>円 ")
            .append("<button type='submit'>追加</button></form></div>")
            .append("<p id='price-error' class='price-error' role='alert'>").append(escapeHtml(priceError)).append("</p>")
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
            // ★変更: 各商品にID付きの編集ボタンを追加
            html.append("<form class='inline' action='/edit' method='get'><input type='hidden' name='id' value='")
                .append(item.id).append("'><button>編集</button></form>");
            html.append("<form class='inline' action='/toggle' method='post'><input type='hidden' name='id' value='")
                .append(item.id).append("'><button>").append(item.purchased ? "未購入に戻す" : "購入済み")
                .append("</button></form>");
            html.append("<form class='inline' action='/delete' method='post'><input type='hidden' name='id' value='")
                .append(item.id).append("'><button>削除</button></form></li>");
        }
        html.append("</ol><form action='/delete-completed' method='post'><button>購入済みを一括削除</button></form>")
            .append("<p class='muted'>JSON API: <a href='/api/items'>/api/items</a></p></div></body></html>");
        // ★変更: JavaScriptでも送信前に金額をチェック（Java側の検証も必ず実行される）
        return html.toString().replace("</body>", "<script>function validatePrice(form){var input=form.elements.price;var value=input.value.trim();var error=document.getElementById('price-error');if(!/^[0-9]+$/.test(value)||Number(value)<1||!Number.isSafeInteger(Number(value))){error.textContent=value===''?'金額を入力してください':'金額は1円以上で入力してください';input.focus();return false;}error.textContent='';return true;}function clearPriceError(){var error=document.getElementById('price-error');if(error)error.textContent='';}</script></body>");
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
