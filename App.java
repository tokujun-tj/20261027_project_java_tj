import com.sun.net.httpserver.HttpServer; // 簡単なWebサーバーを使います。
import java.net.InetSocketAddress; // 待ち受けるポートを指定します。
import java.net.URLDecoder; // 送信された日本語を元に戻します。
import java.nio.charset.StandardCharsets; // 文字コードを指定します。
import java.util.ArrayList; // Todoを入れる一覧を用意します。
import java.util.List; // Todoの一覧を扱います。

class Todo { // 1件分のTodoを表します。★変更
    private final int id; // 何番目かを保存します。★変更
    private final String title; // やることを保存します。★変更
    private boolean done; // 終わったかを保存します。★変更

    Todo(int id, String title) { // Todoの番号と内容を受け取ります。★変更
        this.id = id; // 受け取った番号を保存します。★変更
        this.title = title; // 受け取った内容を保存します。★変更
        this.done = false; // 最初は未完了にします。★変更
    } // コンストラクタの終わりです。★変更

    int getId() { // 番号を読み出します。★変更
        return id; // 番号を返します。★変更
    } // getId メソッドの終わりです。★変更

    String getTitle() { // 内容を読み出します。★変更
        return title; // 内容を返します。★変更
    } // getTitle メソッドの終わりです。★変更

    boolean isDone() { // 完了状態を読み出します。★変更
        return done; // 完了状態を返します。★変更
    } // isDone メソッドの終わりです。★変更

    void setDone(boolean done) { // 完了状態を書き換えます。★変更
        this.done = done; // 受け取った完了状態を保存します。★変更
    } // setDone メソッドの終わりです。★変更
} // Todo クラスの終わりです。★変更

public class App { // クラス名は App です。
    static List<Todo> todos = new ArrayList<>(); // Todoを保存する一覧です。★変更
    static int nextId = 1; // 次に振る番号です。★変更

    public static void main(String[] args) throws Exception { // プログラムの開始位置です。
        load(); // 起動時にtodos.csvからTodoを読み込みます。
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0); // 8080番ポートでサーバーを用意します。
        server.createContext("/", exchange -> { // "/" に来たときの処理をここに直接書きます。
            String path = exchange.getRequestURI().getPath(); // アクセスされたパスを取り出します。
            String method = exchange.getRequestMethod(); // GETやPOSTなどの方法を取り出します。
            String message;
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8"); // 通常の返答はUTF-8のテキストにします。
            if (path.equals("/add") && method.equals("POST")) { // Todoの追加がPOSTで送られたか確かめます。
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8); // 送られた内容をUTF-8で読み取ります。
                String value = body.substring(5); // 「todo=」の後ろの値を取り出します。
                String title = URLDecoder.decode(value, StandardCharsets.UTF_8); // 日本語を元の文字に戻します。★変更
                if (!title.isEmpty()) { // 入力が空でないときだけ追加します。★変更
                    todos.add(new Todo(nextId, title)); // Todoを一覧に追加します。★変更
                    nextId++; // 次に使う番号を進めます。★変更

                    save(); // Todo追加後に全件を保存します。
                } // 空でない入力の処理を終えます。
                exchange.getResponseHeaders().set("Location", "/"); // 戻り先をトップページにします。
                exchange.sendResponseHeaders(303, -1); // トップページへの移動を応答します。
                exchange.close(); // 応答を閉じます。
                return; // この追加処理を終えます。
            } else if (path.equals("/delete-completed") && method.equals("POST")) { // 完了済み一括削除のPOSTを受け取ります。★変更
                todos.removeIf(todo -> todo.isDone()); // 完了済みのTodoだけを一覧から削除します。★変更
                save(); // 完了済みTodoの削除後に全件を保存します。
                exchange.getResponseHeaders().set("Location", "/"); // 削除後の戻り先を一覧画面にします。★変更
                exchange.sendResponseHeaders(303, -1); // 一覧画面への移動を応答します。★変更
                exchange.close(); // 応答を閉じます。★変更
                return; // 一括削除処理を終えます。★変更
            } else if (path.equals("/done") && method.equals("GET")) { // ★追加 完了処理です。
                String query = exchange.getRequestURI().getQuery(); // ★追加 URLのクエリ（?以降）を取り出します。
                if (query != null && query.startsWith("id=") && query.length() > 3) { // ★追加 idが指定されているか確かめます。
                    try { // ★追加 idを数値に変換します。
                        int id = Integer.parseInt(query.substring(3)); // ★追加 idを数値にします。
                        for (Todo todo : todos) { // ★追加 Todoを1件ずつ確認します。
                            if (todo.getId() == id) { // ★追加 idが一致するか確かめます。
                                todo.setDone(true); // ★追加 Todoを完了にします。

                                save(); // Todo完了後に全件を保存します。
                                break; // ★追加 一致したTodoの確認を終えます。
                            } // ★追加 idの一致確認を終えます。
                        } // ★追加 Todoの確認を終えます。
                    } catch (NumberFormatException e) { // ★追加 数字でないidは何も変更しません。
                    } // ★追加 idの数値変換を終えます。
                } // ★追加 idの有無の確認を終えます。
                exchange.getResponseHeaders().set("Location", "/"); // ★追加 戻り先をトップページにします。
                exchange.sendResponseHeaders(303, -1); // ★追加 トップページへの移動を応答します。
                exchange.close(); // ★追加 応答を閉じます。
                return; // ★追加 完了処理を終えます。
            } else if (path.equals("/delete") && method.equals("GET")) { // ★追加 削除処理です。
                String query = exchange.getRequestURI().getQuery(); // ★追加 URLのクエリ（?以降）を取り出します。
                if (query != null && query.startsWith("id=") && query.length() > 3) { // ★追加 idが指定されているか確かめます。
                    try { // ★追加 idを数値に変換します。
                        int id = Integer.parseInt(query.substring(3)); // ★追加 idを数値にします。
                        todos.removeIf(todo -> todo.getId() == id); // ★追加 idが一致するTodoを削除します。

                        save(); // Todo削除後に全件を保存します。
                    } catch (NumberFormatException e) { // ★追加 数字でないidは何も変更しません。
                    } // ★追加 idの数値変換を終えます。
                } // ★追加 idの有無の確認を終えます。
                exchange.getResponseHeaders().set("Location", "/"); // ★追加 戻り先をトップページにします。
                exchange.sendResponseHeaders(303, -1); // ★追加 トップページへの移動を応答します。
                exchange.close(); // ★追加 応答を閉じます。
                return; // ★追加 削除処理を終えます。
            } else if (path.equals("/")) { // トップページを表示します。
                String html = "<!DOCTYPE html><html><head><meta charset='UTF-8'>"
                        + "<style>body{max-width:640px;margin:24px auto;font-size:16px;}</style>"
                        + "</head><body><h1>わたしのTodo</h1>"
                        + "<form method='post' action='/add'><input name='todo'><button>追加</button></form>"; // 見出しとTodo入力フォームを用意します。
                int remainingCount = 0; // 未完了のTodo件数を数えます。★変更
                for (Todo todo : todos) { // Todoを1件ずつ確認します。★変更
                    if (!todo.isDone()) { // 未完了の場合だけ数えます。★変更
                        remainingCount++; // 残り件数を1つ増やします。★変更
                    }
                }
                html += "<p>あと" + remainingCount + "件</p>"; // 一覧の上に残り件数を表示します。★変更
                if (!todos.isEmpty() && remainingCount == 0) { // Todoがあり、すべて完了か確かめます。★変更
                    html += "<p>ぜんぶ完了！</p>"; // すべて完了したときに表示します。★変更
                }
                if (todos.isEmpty()) { // Todoが0件か確かめます。★変更
                    html += "<p>わたしのTodoを入力してください</p>"; // 0件のときだけメッセージを表示します。★変更
                } else { // 1件以上ある場合は、これまでどおり一覧を表示します。★変更
                    html += "<ul>"; // Todoの一覧を用意します。★変更
                    int displayNumber = 1; // 一覧表示用の番号を1から始めます。★変更
                    for (Todo todo : todos) { // Todoを1件ずつ取り出します。★変更
                        String mark = ""; // 完了印を用意します。★変更
                        if (todo.isDone()) { // Todoが完了済みか確かめます。★変更
                            mark = "【●】"; // 完了済みの印を付けます。★変更
                        } // 完了状態の確認を終えます。★変更
                        html += "<li>" + displayNumber + ". " + todo.getTitle() + mark + " <a href='/done?id="
                                + todo.getId()
                                + "'>完了</a> <a href='/delete?id=" + todo.getId() + "'>削除</a></li>"; // ★追加
                                                                                                    // id付きの完了・削除リンクを項目に追加します。
                        displayNumber++; // 次のTodoの番号を1つ進めます。★変更
                    } // Todoをすべて追加しました。
                    html += "</ul>"; // HTMLのリストを閉じます。
                }
                html += "<form method='post' action='/delete-completed'>"
                        + "<button type='submit'>完了済みを一括削除</button></form>"; // 一覧の下に一括削除ボタンを追加します。★変更
                html += "</body></html>"; // HTMLページを閉じます。
                message = html; // 作ったHTMLを返答にします。
                exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8"); // トップページをUTF-8のHTMLとして返します。
            } else {
                message = "ページが見つかりません"; // 見つからないページの返答です。★変更
            }
            byte[] body = message.getBytes("UTF-8"); // 文字をUTF-8のデータにします。
            exchange.sendResponseHeaders(200, body.length); // 成功を示す応答とデータの長さを送ります。
            exchange.getResponseBody().write(body); // データをブラウザに送ります。
            exchange.getResponseBody().close(); // 応答を閉じます。
        }); // "/" の処理を登録します。
        server.start(); // サーバーを起動します。
        System.out.println("サーバー起動: http://localhost:8080 （止めるときは Ctrl+C）"); // 起動メッセージを表示します。
    } // main メソッドの終わりです。

    static void save() { // Todo全件をUTF-8のCSVへ保存します。
        List<String> lines = new ArrayList<>(); // CSVへ書き出す行を準備します。
        for (Todo todo : todos) { // Todoを1件ずつCSV行へ変換します。
            lines.add(todo.getId() + "," + (todo.isDone() ? "1" : "0") + "," + todo.getTitle()); // id、完了状態、タイトルを書き出します。
        }
        try { // ファイル保存時の入出力例外を扱います。
            java.nio.file.Files.write(java.nio.file.Path.of("todos.csv"), lines, StandardCharsets.UTF_8); // UTF-8で全件を保存します。
        } catch (java.io.IOException e) {
            throw new RuntimeException(e); // 保存に失敗したことを通知します。
        }
    }

    static void load() { // todos.csvのTodoを一覧へ読み込みます。
        java.nio.file.Path path = java.nio.file.Path.of("todos.csv"); // 保存ファイルの場所を指定します。
        if (!java.nio.file.Files.exists(path)) { // 保存ファイルが無ければ空のまま起動します。
            return; // 読み込み処理を終了します。
        }
        int maxId = 0; // 読み込んだ最大idを記録します。
        try { // ファイル読み込み時の入出力例外を扱います。
            for (String line : java.nio.file.Files.readAllLines(path, StandardCharsets.UTF_8)) { // CSVをUTF-8で1行ずつ読み込みます。
                String[] fields = line.split(",", 3); // id、done、titleの3項目に分割します。
                if (fields.length != 3) { // 不正な行は読み飛ばします。
                    continue; // 次の行へ進みます。
                }
                int id = Integer.parseInt(fields[0].trim()); // idを読み込みます。
                Todo todo = new Todo(id, fields[2]); // 読み込んだタイトルでTodoを作ります。
                todo.setDone(fields[1].trim().equals("1")); // doneが1なら完了済みにします。
                todos.add(todo); // Todoを一覧へ戻します。
                if (id > maxId) { // 最大idを更新します。
                    maxId = id; // 最大idを記録します。
                }
            }
            nextId = maxId + 1; // 最大idの次の番号を次回のidにします。
        } catch (java.io.IOException e) {
            throw new RuntimeException(e); // 読み込みに失敗したことを通知します。
        }
    }
} // App クラスの終わりです。
