package proxy;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import cache.CacheEntry;
import cache.CacheManager;


public class ProxyServer {

    String serverIP;
    int proxyPort;
    String targetHost;
    int targetPort;
    String protocole;
    String fileConfig = "config.conf";
    CacheManager cacheManager;

    public CacheManager getCacheManager() {
        return cacheManager;
    }

    // Nouveau champ pour arrêter le serveur proprement
    private volatile boolean isRunning = false;

    public boolean isRunning() {
        return isRunning;
    }

    public ProxyServer() {
        loadConfig();
        this.cacheManager = new CacheManager();
    }


    private void loadConfig() {
        Properties config = new Properties();

        try (FileInputStream inputStream = new FileInputStream(fileConfig)) {
            config.load(inputStream);

            // Chargement des valeurs depuis le fichier .conf
            serverIP = config.getProperty("ip_proxy");
            proxyPort = Integer.parseInt(config.getProperty("port_proxy"));
            targetHost = config.getProperty("nom_server");
            targetPort = Integer.parseInt(config.getProperty("port_server"));
            protocole = config.getProperty("protocole");

        } catch (IOException e) {
            System.err.println("Error loading configuration file.");
        }
    }

    public void startProxyServer() {
        isRunning = true;
        try (ServerSocket serverSocket = new ServerSocket(proxyPort, 0, InetAddress.getByName(serverIP))) {
            System.out.println("Proxy démarré sur le port : " + proxyPort);
            while (isRunning) {
                // Accepter une connexion client
                Socket clientSocket = serverSocket.accept();

                // Gérer la requête dans un thread séparé
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
           System.err.println("Erreur lors de l'écoute de l'adresse IP. L'adresse n'est pas utilisée.");
        }
    }

    public void stopProxyServer() {
        isRunning = false;
        System.out.println("Proxy arrêté.");
    }

    private void handleClient(Socket clientSocket) {
        try (
            InputStream clientInput = clientSocket.getInputStream();
            OutputStream clientOutput = clientSocket.getOutputStream()
        ) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(clientInput));
            String requestLine = reader.readLine();
            if (requestLine == null) {
                return;  
            }

            String[] tokens = requestLine.split(" ");
            String method = tokens[0];
            String url = tokens.length > 1 ? tokens[1] : "/";

            // Verifier le protocole
            if(!url.startsWith(protocole)) {
                sendHttpResponse(clientOutput, 403, "Forbidden", "Only HTTP protocol is allowed", "text/html");
                return;
            }

            if (!isRequestToTargetHost(url)) {
                sendHttpResponse(clientOutput, 403, "Forbidden", "Access Denied: Invalid target", "text/html");
                return;
            }

            if (!method.equalsIgnoreCase("GET")) {
                sendHttpResponse(clientOutput, 501, "Not Implemented", "This method is not implemented", "text/html");
                return;
            }

            // Vérifier si la réponse est dans le cache
            CacheEntry cacheEntry = cacheManager.get(url);
            if (cacheEntry != null) {
                System.out.println("Cache hit for: " + url);
                sendHttpResponse(clientOutput, 200, "OK", cacheEntry.getData(), cacheEntry.getContentType());
                return;
            }

            // Si la réponse n'est pas en cache, obtenir depuis le serveur cible
            byte[] responseData = fetchFromServer(url);
            if (responseData != null) {
                CacheEntry entry = cacheManager.get(url); // Vérifier le type MIME
                String contentType = entry != null ? entry.getContentType() : "application/octet-stream";
                sendHttpResponse(clientOutput, 200, "OK", responseData, contentType);
            } else {
                sendHttpResponse(clientOutput, 500, "Internal Server Error", "Error fetching data from target server", "text/html");
            }
        } catch (IOException e) {
            try {
                sendHttpResponse(clientSocket.getOutputStream(), 500, "Internal Server Error", "Error processing the request", "text/html");
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        } finally {
            try {
                clientSocket.close();
                cacheManager.shutdown();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private boolean isRequestToTargetHost(String url) {
        try {
            URL parsedUrl = new URL(url);
            return parsedUrl.getHost().equalsIgnoreCase(serverIP) && parsedUrl.getPort() == proxyPort;
        } catch (MalformedURLException e) {
            return false;
        }
    }


    // Fonction pour récupérer les données du serveur cible et les stocker dans le cache
    private byte[] fetchFromServer(String url) throws IOException {
        URL parsedUrl = new URL(url);
        String path = parsedUrl.getPath();
        String query = parsedUrl.getQuery();
        String targetUrl = protocole + targetHost + ":" + targetPort + path + (query != null ? "?" + query : "");
    
        System.out.println("Redirection vers le serveur cible : " + targetUrl);
        HttpURLConnection connection = (HttpURLConnection) new URL(targetUrl).openConnection();
        connection.setRequestMethod("GET");
    
        // Vérifier le code de statut HTTP
        int statusCode = connection.getResponseCode();
        if (statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
            System.out.println("Ressource non trouvée : " + targetUrl);
            return null;
        }
    
        // Récupérer le type MIME
        String contentType = connection.getContentType();
        System.out.println("Content-Type détecté : " + contentType);
    
        // Lire les données binaires du serveur cible
        try (InputStream targetInput = connection.getInputStream();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] data = new byte[4096];
            int bytesRead;
            while ((bytesRead = targetInput.read(data)) != -1) {
                buffer.write(data, 0, bytesRead);
            }
    
            // Ajouter au cache
            cacheManager.add(url, buffer.toByteArray(), contentType);
            return buffer.toByteArray();
        }
    }    
    

    // Fonction générique pour envoyer une réponse HTTP au client
    private void sendHttpResponse(OutputStream clientOutput, int statusCode, String statusText, byte[] contentData, String contentType) throws IOException {
        PrintWriter writer = new PrintWriter(clientOutput, true);
        writer.println("HTTP/1.1 " + statusCode + " " + statusText);
        writer.println("Content-Type: " + contentType);
        writer.println("Content-Length: " + contentData.length);
        writer.println("");
        clientOutput.write(contentData);
        clientOutput.flush();
    }

    // Envoi d'une réponse HTML simple au client
    private void sendHttpResponse(OutputStream clientOutput, int statusCode, String statusText, String message, String contentType) throws IOException {
        String content = "<html><body><h1>" + statusText + "</h1><p>" + message + "</p></body></html>";
        sendHttpResponse(clientOutput, statusCode, statusText, content.getBytes("UTF-8"), contentType);
    }
}
