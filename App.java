import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// ★変更: Todoを買い物リストの商品データに変更
class ShoppingItem {
    private final int id;
    private final String name;
    private final String category;
    private final int price;
    private boolean purchased;
    ShoppingItem(int id, String name, String category, int price, boolean purchased) { this.id=id; this.name=name; this.category=category; this.price=price; this.purchased=purchased; }
    int getId() { return id; }
    String getName() { return name; }
    String getCategory() { return category; }
    int getPrice() { return price; }
    boolean isPurchased() { return purchased; }
    void setPurchased(boolean value) { purchased=value; }
}

public class App {
    // ★変更: 買い物リストと保存先
    static final List<ShoppingItem> items = new ArrayList<>();
    static int nextId = 1;
    static final Path CSV_PATH = Path.of("shopping.csv");

    public static void main(String[] args) throws Exception {
        // ★変更: 起動時にshopping.csvを読み込む
        load();
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", App::handleRequest);
        server.start();
        System.out.println("買い物リストを起動しました: http://localhost:8080");
        System.out.println("終了するには Ctrl+C を押してください。");
    }

    // ★変更: ブラウザからのリクエストを処理する
    static void handleRequest(HttpExchange exchange) throws IOException {
        String path=exchange.getRequestURI().getPath(), method=exchange.getRequestMethod();
        if (path.equals("/") && method.equals("GET")) sendHtml(exchange, createPage());
        else if (path.equals("/add") && method.equals("POST")) { addItem(parseForm(readBody(exchange))); redirect(exchange); }
        else if (path.equals("/toggle") && method.equals("GET")) { changePurchased(getId(exchange)); redirect(exchange); }
        else if (path.equals("/delete") && method.equals("GET")) { deleteItem(getId(exchange)); redirect(exchange); }
        else if (path.equals("/delete-purchased") && method.equals("POST")) { items.removeIf(ShoppingItem::isPurchased); save(); redirect(exchange); }
        else sendText(exchange, 404, "ページが見つかりません");
    }

    // ★変更: 商品名・カテゴリ・価格を受け取り、未購入で追加する
    static void addItem(Map<String,String> form) {
        String name=form.getOrDefault("name","").trim(), category=form.getOrDefault("category","その他");
        int price;
        try { price=Integer.parseInt(form.getOrDefault("price","0").trim()); } catch(NumberFormatException e) { price=0; }
        if (!name.isEmpty() && price>=0) { items.add(new ShoppingItem(nextId++,name,category,price,false)); save(); }
    }
    static void changePurchased(int id) { for(ShoppingItem item:items) if(item.getId()==id) { item.setPurchased(!item.isPurchased()); save(); return; } }
    static void deleteItem(int id) { items.removeIf(item->item.getId()==id); save(); }

    // ★変更: 買い物リスト画面をHTMLで作成する
    static String createPage() {
        int remaining=0, remainingPrice=0, purchasedPrice=0;
        for(ShoppingItem item:items) if(item.isPurchased()) purchasedPrice+=item.getPrice(); else { remaining++; remainingPrice+=item.getPrice(); }
        StringBuilder h=new StringBuilder();
        h.append("<!DOCTYPE html><html lang='ja'><head><meta charset='UTF-8'><meta name='viewport' content='width=device-width, initial-scale=1'><title>買い物リスト</title><style>");
        h.append("body{font-family:Arial,sans-serif;max-width:800px;margin:30px auto;padding:0 16px;background:#f7f9fc;color:#263238}h1{color:#1769aa}.box{background:white;padding:18px;margin:14px 0;border-radius:10px;box-shadow:0 2px 8px #ccd}input,select,button{font-size:16px;padding:8px;margin:4px 2px}input[name=name]{width:230px}button{cursor:pointer;border:0;border-radius:6px;background:#1769aa;color:white}.danger{background:#c62828}table{width:100%;border-collapse:collapse;background:white}.item-row{border-bottom:1px solid #ddd}td{padding:10px}.purchased{color:#777;background:#e8f5e9}.purchased .item-name{text-decoration:line-through}.price{text-align:right;white-space:nowrap}.actions{white-space:nowrap}.small{font-size:13px;color:#607d8b}</style></head><body><h1>🛒 買い物リスト</h1>");
        h.append("<div class='box'><form method='post' action='/add'><input name='name' placeholder='商品名' required><select name='category'><option>食品</option><option>日用品</option><option>その他</option></select><input name='price' type='number' min='0' value='0' placeholder='予定価格'> 円<button type='submit'>追加</button></form></div>");
        h.append("<div class='box'><strong>未購入: ").append(remaining).append("件</strong>　未購入予定額: ").append(formatYen(remainingPrice)).append("　購入済み金額: ").append(formatYen(purchasedPrice));
        if(!items.isEmpty() && remaining==0) h.append("<p><strong>🎉 ぜんぶ買えました！</strong></p>");
        h.append("</div>");
        if(items.isEmpty()) h.append("<div class='box'>商品を追加してください。</div>");
        else { h.append("<div class='box'><table><tr><th>No.</th><th>商品</th><th>カテゴリ</th><th>価格</th><th>操作</th></tr>"); int no=1;
            for(ShoppingItem item:items) { h.append("<tr class='item-row").append(item.isPurchased()?" purchased":"").append("'><td>").append(no++).append("</td><td class='item-name'>").append(escapeHtml(item.getName())).append("</td><td>").append(escapeHtml(item.getCategory())).append("</td><td class='price'>").append(formatYen(item.getPrice())).append("</td><td class='actions'><a href='/toggle?id=").append(item.getId()).append("'>").append(item.isPurchased()?"未購入に戻す":"購入済みにする").append("</a> <a href='/delete?id=").append(item.getId()).append("'>削除</a></td></tr>"); }
            h.append("</table><form method='post' action='/delete-purchased' style='margin-top:14px'><button class='danger' type='submit'>購入済み商品を一括削除</button></form></div>"); }
        h.append("<p class='small'>データはshopping.csvに保存されます。</p></body></html>"); return h.toString();
    }
    static String formatYen(int p) { return String.format("%,d円",p); }
    static String escapeHtml(String s) { return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;"); }

    static int getId(HttpExchange e) { String q=e.getRequestURI().getQuery(); if(q!=null) for(String part:q.split("&")) { String[] p=part.split("=",2); if(p.length==2&&p[0].equals("id")) try{return Integer.parseInt(p[1]);}catch(NumberFormatException ignored){return -1;} } return -1; }
    static String readBody(HttpExchange e)throws IOException { return new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8); }
    static Map<String,String> parseForm(String body) { Map<String,String> m=new HashMap<>(); for(String part:body.split("&")){String[] p=part.split("=",2);if(p.length==2)m.put(URLDecoder.decode(p[0],StandardCharsets.UTF_8),URLDecoder.decode(p[1],StandardCharsets.UTF_8));}return m; }
    static void redirect(HttpExchange e)throws IOException { e.getResponseHeaders().set("Location","/");e.sendResponseHeaders(303,-1);e.close(); }
    static void sendHtml(HttpExchange e,String s)throws IOException { byte[] b=s.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","text/html; charset=UTF-8");e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close(); }
    static void sendText(HttpExchange e,int status,String s)throws IOException { byte[] b=s.getBytes(StandardCharsets.UTF_8);e.getResponseHeaders().set("Content-Type","text/plain; charset=UTF-8");e.sendResponseHeaders(status,b.length);e.getResponseBody().write(b);e.close(); }

    // ★変更: shopping.csvへ保存する
    static void save() { List<String> lines=new ArrayList<>(); for(ShoppingItem i:items) lines.add(i.getId()+","+i.isPurchased()+","+csv(i.getCategory())+","+i.getPrice()+","+csv(i.getName())); try{Files.write(CSV_PATH,lines,StandardCharsets.UTF_8);}catch(IOException e){throw new RuntimeException("shopping.csvを保存できませんでした",e);} }
    // ★変更: shopping.csvから読み込む
    static void load() { if(!Files.exists(CSV_PATH))return; int max=0; try{for(String line:Files.readAllLines(CSV_PATH,StandardCharsets.UTF_8)){List<String> f=parseCsvLine(line);if(f.size()!=5)continue;try{int id=Integer.parseInt(f.get(0)),price=Integer.parseInt(f.get(3));items.add(new ShoppingItem(id,f.get(4),f.get(2),price,Boolean.parseBoolean(f.get(1))));max=Math.max(max,id);}catch(NumberFormatException ignored){}}nextId=max+1;}catch(IOException e){throw new RuntimeException("shopping.csvを読み込めませんでした",e);} }
    static String csv(String s) { return "\""+s.replace("\"","\"\"").replace("\n"," ").replace("\r"," ")+"\""; }
    static List<String> parseCsvLine(String line) { List<String> r=new ArrayList<>();StringBuilder f=new StringBuilder();boolean q=false;for(int i=0;i<line.length();i++){char c=line.charAt(i);if(c=='"'){if(q&&i+1<line.length()&&line.charAt(i+1)=='"'){f.append('"');i++;}else q=!q;}else if(c==','&&!q){r.add(f.toString());f.setLength(0);}else f.append(c);}r.add(f.toString());return r; }
}
