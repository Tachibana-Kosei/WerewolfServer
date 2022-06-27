import java.io.*;
import java.net.*;
import java.util.*;

/*多人数間でのテキストチャットを実現するサーバー。
* クライアントからテキストを受け取り、そのままそれを全クライアントに送信する。
* こっちはIntelliJでそのまま実行しちゃってOK。引数不要。
* サーバーを閉じる機能は実装してないので、Ctrl+Cで強制終了してね*/

public class TextChatServer {
	static ServerSocket serverSocket = null;
	static SendThread sendThread = null;
	static ArrayList<Client> clients = new ArrayList<>();
	//やりとりしたデータをログとして残すためのリスト。未実装。
	static ArrayList<byte[]> log = new ArrayList<>();

	//main()では、初期設定を行った後は新規クライアントの受付のみを行う。
	public static void main(String[] args) {
		boolean check = true;
		//サーバーソケットの作成
		try {
			serverSocket = new ServerSocket(6000, 300);
			System.out.println("TextChat Server Online.(port=" + serverSocket.getLocalPort() + ")\n");
		} catch (IOException e) {
			System.out.println("Error in Server boot");
			System.exit(1);
		}
		//メッセージの送信用スレッドの作成と開始
		sendThread = new SendThread();
		sendThread.start();
		//新規クライアントの受付を行うwhileループ
		//Clientを受け付け次第、メッセージの受信用スレッド(ReadThread)の作成と開始を行う。
		while (check) {
			try {
				Socket socket = serverSocket.accept();
				System.out.println("Connected client : " + socket.getRemoteSocketAddress());
				Client newClient = new Client(socket);
				clients.add(newClient);
				ReadThread newReadThread = new ReadThread(newClient);
				newReadThread.start();
			} catch (IOException e) {
				System.out.println("Error by create socket");
				System.exit(1);
			}
		}
	}

	/*メッセージの送信を行うスレッド。スレッドで実装する意味はもしかしたらないかもしれない。なんならクラスとしての実装の必要性も疑わしい。
	* ReadThreadと違って、こっちのインスタンスは1つだけ作成される。*/
	public static class SendThread extends Thread {
		@Override
		public synchronized void start() {
			super.start();
			setName("sendThread");
		}

		public void run() {
			System.out.println("run sendThread");
		}

		// 現在接続しているクライアント全員に同じデータを送るメソッド。
		public static void sendBytes(byte[] bytes) {
			for (Client client : clients) {
				try {
					client.outputStream.write(bytes);
				} catch (IOException e) {
					System.out.println("Error by write() to All");
					System.exit(1);
				}
			}
		}

		// 第2引数のクライアントにだけ向けてデータを送信するメソッド。人狼や占い師専用メッセージの他、個別の接続に関するメッセージの送信に使う
		public static void sendBytes(byte[] bytes, Client client) {
			try {
				client.outputStream.write(bytes);
			} catch (IOException e) {
				System.out.println("Error by write() to " + client.name);
				System.exit(1);
			}
		}
	}

	/*クライアントからのデータ（テキスト）を受け取るスレッドクラス。
	* クライアントから接続があると、クライアントごとに新しくインスタンスが作成される。
	* インスタンスができると、start()→run()の順番で実行される。*/
	public static class ReadThread extends Thread {
		final Client client;
		byte[] buff = new byte[1024];
		int n = 0;
		private String outStr;

		public ReadThread(Client client) {
			this.client = client;
		}

		public ReadThread(Socket socket) {
			this.client = new Client(socket);
		}

		@Override
		public synchronized void start() {
			System.out.println("read socket start");
			//クライアントの名前を入力させる処理。run()でやった方がいいのか？わからん
			if (client.name == null) {
				do {
					outStr = "Input your name >";
					SendThread.sendBytes(outStr.getBytes(), client);
					try {
						n = client.inputStream.read(buff);
					} catch (IOException e) {
						System.out.println("Error in read() of client name");
						System.exit(1);
					}
				} while (n <= 2);
				client.name = new String(Arrays.copyOfRange(buff, 0, n - 2));
			}
			//新規参加者の通知を全員に送信。
			outStr = client.name + " is joined.\n" + clients.size() + " clients connecting now.\n";
			SendThread.sendBytes(outStr.getBytes());
			super.start();
		}

		@Override
		public void run() {
			boolean check = true;
			while (check) {
				/*クライアントからの入力を受け取るdo-while文。
				* 改行だけのデータ（つまり2バイトのデータ）は無視する。*/
				do {
					SendThread.sendBytes(">".getBytes());
					try {
						n = client.inputStream.read(buff);
					} catch (IOException e) {
						System.out.println("Error in read()");
						System.exit(1);
					}
				} while (n <= 2);
				/*クライアントから送られてきたデータをまるっと文字列に変換する*/
				String readStr = new String(Arrays.copyOfRange(buff, 0, n));
				/*クライアントの名前と送られてきた文字列を結合する*/
				outStr = client.name + " : " + readStr;
				//クライアントから「.quit」または「.exit」という文字列が送られてきたら、そのクライアントを切断する。
				if (readStr.equals(".quit\r\n") || readStr.equals(".exit\r\n")) {
					check = false;
					clients.remove(client);
					int connectedNum = clients.size();
					outStr = client.name + " is exited.\n" + connectedNum + " clients are connecting now.\n";
					SendThread.sendBytes(".disconnect".getBytes(), client);
				}
				//データを全クライアントに送信する
				SendThread.sendBytes(outStr.getBytes());
			}
		}
	}

	//クライアントの情報をもろもろ格納しているクラス。
	public static class Client {
		String name = null;
		Socket socket;
		InputStream inputStream;
		OutputStream outputStream;


		public Client(Socket socket) {
			this.socket = socket;
			try {
				this.inputStream = socket.getInputStream();
				this.outputStream = socket.getOutputStream();
			} catch (IOException e) {
				System.out.println("Client Connect Error");
			}
		}
	}
}
