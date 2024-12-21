package proxy;

import java.io.PrintWriter;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import proxy.ProxyServer;

public class Service {

    private ProxyServer proxyServer;
    private ExecutorService executorService;

    public Service() {
        this.proxyServer = new ProxyServer();
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public void run() {
        System.out.println("Tapez 'start' pour démarrer le serveur proxy, 'stop' pour l'arrêter et quitter.");
        System.out.println("Tapez 'clear' pour vider le cache, 'show' pour afficher le cache, ou 'delete <url>' pour supprimer une entrée.");

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                String command = scanner.nextLine().trim();

                switch (command.toLowerCase()) {
                    case "start":
                        startServer();
                        break;
                    case "stop":
                        stopServer();
                        return; 
                    case "clear":
                        proxyServer.getCacheManager().clear();
                        System.out.println("Cache vidé.");
                        break;
                    case "show":
                        proxyServer.getCacheManager().showCacheContents();
                        break;
                    default:
                        if (command.startsWith("delete ")) {
                            String url = command.substring(7).trim();
                            proxyServer.getCacheManager().remove(url);
                            System.out.println("Entrée supprimée du cache pour l'URL : " + url);
                        } else {
                            System.out.println("Commande inconnue.");
                        }
                        break;
                }
            }
        }
    }

    private void startServer() {
        if (proxyServer.isRunning()) {
            System.out.println("Le serveur proxy est déjà en cours d'exécution.");
            return;
        }
        executorService.submit(proxyServer::startProxyServer);
        System.out.println("Commande pour démarrer le serveur proxy envoyée.");
    }

    private void stopServer() {
        if (proxyServer.isRunning()) {
            proxyServer.stopProxyServer();
        }
        executorService.shutdownNow();
        System.out.println("Commande pour arrêter le serveur proxy envoyée.");
    }
}
