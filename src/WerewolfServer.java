import java.io.*;
import java.net.*;
import java.util.*;

public class WerewolfServer {
	enum Step {
		Game_Setting, Daytime_Discussion, Vote, Night_Action, Morning
	}

	enum Role {
		Werewolf, Fortune_teller, Citizen
	}

	static ServerSocket serverSocket = null;
	static SendThread sendThread = null;
	static GameMasterThread gameMasterThread = null;
	static ArrayList<Client> clients = new ArrayList<>();
	//やりとりしたデータをログとして残すためのリスト。未実装。
	static ArrayList<byte[]> log = new ArrayList<>();
	static String textHead = ".text.";
	static String nameHead = ".nameset.";
	static boolean gameStart = false;
	static Step step = Step.Game_Setting;

	//main()では、初期設定を行った後は新規クライアントの受付のみを行う。
	public static void main(String[] args) {
		//サーバーソケットの作成
		try {
			serverSocket = new ServerSocket(6000, 300);
			System.out.println("Werewolf Server Online.(port=" + serverSocket.getLocalPort() + ")\n");
		} catch (IOException e) {
			System.out.println("Error in Server boot");
			System.exit(1);
		}
		//メッセージの送信用スレッドの作成と開始
		sendThread = new SendThread();
		sendThread.start();
		//クライアント受付用スレッドの作成と開始
		AcceptThread acceptThread = new AcceptThread();
		acceptThread.start();
		//ゲーム進行スレッドの作成と開始
		gameMasterThread = new GameMasterThread();
		gameMasterThread.start();
	}

	//ゲームの進行を担当し、主に表示処理を行う。
	public static class GameMasterThread extends Thread {
		static int dayNum;
		static ArrayList<Client> aliveList;
		static Client killedMan;

		@Override
		public synchronized void start() {
			System.out.println("Start GameMaster");
			//クライアントが揃い、ゲーム開始の命令があるまで待機
			while (true) {
				if (gameStart) {
					if (clients.size() < 4) {
						System.out.println(clients.size() + " clients");
						gameStart = false;
					} else break;
				}
				try {
					sleep(100);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
			//役職の割り振り
			clients.forEach(x -> x.role = null);
			Collections.shuffle(clients);
			clients.get(0).role = Role.Werewolf;
			clients.get(1).role = Role.Fortune_teller;
			for (int i = 2; i < clients.size(); i++) {
				clients.get(i).role = Role.Citizen;
			}
			Collections.shuffle(clients);
			//初期値設定
			dayNum = 1;
			clients.forEach(x -> {
				x.alive = true;
				x.role = null;
				x.selected = false;
				x.selectNum = 0;
				x.votes_cast = 0;
			});
			aliveList = new ArrayList<>(clients.stream().filter(x -> x.alive).toList());
			//ゲーム開始の通知
			SendThread.sendData(textHead + "Game Start\n");
			//役職、ゲームルールの通知
			SendThread.sendData(textHead + aliveList.size() + " Players :\n");
			for (Client client : aliveList) {
				SendThread.sendText(client.name + "\n");
			}
			for (Client client : aliveList) {
				SendThread.sendText("Your role is " + client.role + "\n", client);
			}
			super.start();
		}

		@Override
		public void run() {
			SendThread.sendText("  --Day " + dayNum + "--  \n");
			while (true) {
				//昼会議(5分)開始
				step = Step.Daytime_Discussion;
				SendThread.sendData(textHead + "Start daytime discussion\n");
				long startTime = System.currentTimeMillis();
				while ((System.currentTimeMillis() - startTime) < 300000) {
					try {
						sleep(100);
					} catch (InterruptedException e) {
						throw new RuntimeException(e);
					}
				}
				//投票(1分)開始(初日はスキップ)
				if (dayNum != 1) {
					step = Step.Vote;
					SendThread.sendText("Start vote\n");
					aliveList = new ArrayList<>(aliveList.stream().filter(x -> x.alive).toList());
					aliveList.forEach(System.out::println);
					aliveList.forEach(x -> {
						x.selected = false;
						x.selectNum = 0;
						x.votes_cast = 0;
					});
					SendThread.sendText("0 : Skip\n");
					for (int i = 0; i < aliveList.size(); i++) {
						SendThread.sendText((i + 1) + " : " + aliveList.get(i).name + "\n");
					}
					SendThread.sendText(">");
					startTime = System.currentTimeMillis();
					while ((System.currentTimeMillis() - startTime) < 60000) {
						try {
							sleep(100);
							if (aliveList.stream().allMatch(x-> x.selected)){
								break;
							}
						} catch (InterruptedException e) {
							throw new RuntimeException(e);
						}
					}
					aliveList = new ArrayList<>(aliveList.stream().filter(x -> x.alive).toList());
					aliveList.forEach(System.out::println);
					aliveList.forEach(x -> {
						x.selected = false;
						x.selectNum = 0;
					});
					long aliveWerewolfNum = aliveList.stream().filter(x -> x.role.equals(Role.Werewolf)).count();
					long aliveFTNum = aliveList.stream().filter(x -> x.role.equals(Role.Fortune_teller)).count();
					long aliveCitizenNum = aliveList.stream().filter(x -> x.role.equals(Role.Citizen)).count();
					if (aliveWerewolfNum == 0) {
						SendThread.sendText("Human's Win !");
						break;
					} else if (aliveFTNum + aliveCitizenNum <= aliveWerewolfNum) {
						SendThread.sendText("Werewolf's Win !");
						break;
					}
				}
				//夜のアクション(1分)開始
				SendThread.sendText("Start night action\n");
				step = Step.Night_Action;
				aliveList.forEach(x -> {
					x.selected = false;
					x.selectNum = 0;
					x.votes_cast = 0;
				});
				startTime = System.currentTimeMillis();
				while ((System.currentTimeMillis() - startTime) < 60000) {
					try {
						sleep(100);
						if (aliveList.stream().allMatch(x-> x.selected)){
							break;
						}
					} catch (InterruptedException e) {
						throw new RuntimeException(e);
					}
				}
				aliveList = new ArrayList<>(aliveList.stream().filter(x -> x.alive).toList());
				aliveList.forEach(System.out::println);
				dayNum++;
				//食べられた人間の発表
				SendThread.sendText("  --Day " + dayNum + "--  \n");
				SendThread.sendText("Good morning, everyone.\n");
				step = Step.Morning;
				try {
					sleep(1000);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
				SendThread.sendText(killedMan.name + " was found cruelly disfigured.\n");
				long aliveWerewolfNum = aliveList.stream().filter(x -> x.role.equals(Role.Werewolf)).count();
				long aliveFTNum = aliveList.stream().filter(x -> x.role.equals(Role.Fortune_teller)).count();
				long aliveCitizenNum = aliveList.stream().filter(x -> x.role.equals(Role.Citizen)).count();
				if (aliveWerewolfNum == 0) {
					SendThread.sendText("Human's Win !");
					break;
				} else if (aliveFTNum + aliveCitizenNum <= aliveWerewolfNum) {
					SendThread.sendText("Werewolf's Win !");
					break;
				}
				try {
					sleep(1000);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
				//怪しい人の発表
				SendThread.sendText("is being suspicious.\n");
				try {
					sleep(1000);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
		}
	}

	public static class AcceptThread extends Thread {
		@Override
		public void run() {
			//新規クライアントの受付を行うwhileループ
			//Clientを受け付け次第、メッセージの受信用スレッド(ReadThread)の作成と開始を行う。
			while (true) {
				try {
					Socket socket = serverSocket.accept();
					System.out.println("Connected client : " + socket.getRemoteSocketAddress());
					Client newClient = new Client(socket);
					clients.add(newClient);
					ReadThread newReadThread = new ReadThread(newClient);
					newReadThread.start();
					if (clients.size() >= 6) break;
				} catch (IOException e) {
					System.out.println("Error by create socket");
					System.exit(1);
				}
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
		public static void sendData(byte[] bytes) {
			for (Client client : clients) {
				sendData(bytes, client);
			}
		}

		// 第2引数のクライアントにだけ向けてデータを送信するメソッド。人狼や占い師専用メッセージの他、個別の接続に関するメッセージの送信に使う
		public static void sendData(byte[] bytes, Client client) {
			try {
				client.outputStream.write(bytes);
			} catch (IOException e) {
				System.out.println("Error by write() to " + client.name);
				System.exit(1);
			}
		}

		public static void sendData(String str) {
			sendData(str.getBytes());
		}

		public static void sendData(String str, Client client) {
			sendData(str.getBytes(), client);
		}

		public static void sendText(String str) {
			sendData((textHead + str).getBytes());
		}

		public static void sendText(String str, Client client) {
			sendData((textHead + str).getBytes(), client);
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
					outStr = textHead + "Input your name >";
					SendThread.sendData(outStr.getBytes(), client);
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
			outStr = textHead + client.name + " is joined.\n" + clients.size() + " clients connecting now.\n";
			SendThread.sendData(outStr.getBytes());
			outStr = nameHead + client.name;
			SendThread.sendData(outStr.getBytes(), client);
			super.start();
		}

		@Override
		public void run() {
			boolean check = true;
			while (check) {
				/*クライアントからの入力を受け取るdo-while文。
				 * 改行だけのデータ（つまり2バイトのデータ）は無視する。*/
				do {
					SendThread.sendData((textHead + ">").getBytes());
					try {
						n = client.inputStream.read(buff);
					} catch (IOException e) {
						System.out.println("Error in read()");
						System.exit(1);
					}
				} while (n <= 2);
				/*クライアントから送られてきたデータをまるっと文字列に変換する*/
				String readStr = new String(Arrays.copyOfRange(buff, 0, n));
				switch (step) {
					case Game_Setting -> {
						/*クライアントの名前と送られてきた文字列を結合する*/
						outStr = client.name + " : " + readStr;
						//クライアントから「.quit」または「.exit」という文字列が送られてきたら、そのクライアントを切断する。
						if (readStr.equals(".quit\r\n") || readStr.equals(".exit\r\n")) {
							check = false;
							clients.remove(client);
							int connectedNum = clients.size();
							outStr = client.name + " is exited.\n" + connectedNum + " clients are connecting now.\n>";
							SendThread.sendData(".disconnect".getBytes(), client);
						}
						//クライアントから「.gamestart」という文字列が送られてきたら、ゲームを開始する
						if (readStr.equals(".gamestart\r\n")) {
							gameStart = true;
						}
						//データを全クライアントに送信する
						SendThread.sendText(outStr);
					}
					case Daytime_Discussion -> {
						/*クライアントの名前と送られてきた文字列を結合する*/
						outStr = client.name + " : " + readStr;
						//データを全クライアントに送信する
						SendThread.sendText(outStr);
					}
					case Vote -> {
						if (!client.selected && readStr.matches("[+-]?\\d*(\\.\\d+)?")) {
							int n = Integer.parseInt(readStr);
							if (n <= GameMasterThread.aliveList.size()) {
								client.selected = true;
								client.selectNum = n;
							}
						}
					}
					case Night_Action -> {
						if (!client.selected && readStr.matches("[+-]?\\d*(\\.\\d+)?")) {
							int n = Integer.parseInt(readStr);
							if (n <= GameMasterThread.aliveList.size()) {
								client.selected = true;
								client.selectNum = n;
								if (client.selectNum > 0) {
									Client selectedClient = GameMasterThread.aliveList.get(n - 1);
									switch (client.role) {
										case Werewolf -> {
											selectedClient.alive = false;
											GameMasterThread.killedMan = selectedClient;
											SendThread.sendText("You killed " + selectedClient.name + ".", client);
										}
										case Fortune_teller -> {
											SendThread.sendText(selectedClient.name + " is " + selectedClient.role, client);
										}
										case Citizen -> {
											selectedClient.votes_cast++;
										}
									}
								}
							}
						}
					}
					case Morning -> {
					}
				}
			}
		}
	}

	//クライアントの情報をもろもろ格納しているクラス。
	public static class Client {
		String name = null;
		Role role = null;
		boolean alive = true;
		boolean selected = false;
		int selectNum;
		int votes_cast = 0;
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
