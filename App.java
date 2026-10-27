import com.sun.net.httpserver.HttpServer; // 簡単なWebサーバーを使います。
import java.net.InetSocketAddress; // 待ち受けるポートを指定します。

public class App { // クラス名は App です。
    public static void main(String[] args) throws Exception { // プログラムの開始位置です。
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0); // 8080番ポートでサーバーを用意します。
        server.createContext("/", exchange -> { // "/" に来たときの処理をここに直接書きます。
            String message = "Hello, Server!"; // ブラウザに返す文字です。
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8"); // 返す文字がUTF-8だと伝えます。
            byte[] body = message.getBytes("UTF-8"); // 文字をUTF-8のデータにします。
            exchange.sendResponseHeaders(200, body.length); // 成功を示す応答とデータの長さを送ります。
            exchange.getResponseBody().write(body); // データをブラウザに送ります。
            exchange.getResponseBody().close(); // 応答を閉じます。
        }); // "/" の処理を登録します。
        server.start(); // サーバーを起動します。
        System.out.println("サーバー起動: http://localhost:8080 （止めるときは Ctrl+C）"); // 起動メッセージを表示します。
    } // main メソッドの終わりです。
} // App クラスの終わりです。
