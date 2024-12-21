package cache;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class CacheManager {

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private long cacheExpirationTime;
    private int maxEntries;
    private long maxCacheSize;

    private final AtomicLong currentCacheSize = new AtomicLong(0); // Taille actuelle du cache en mémoire

    private final String fileConfig = "config.conf";

    public CacheManager() {
        loadConfig();
        // Planification du nettoyage automatique
        scheduler.scheduleAtFixedRate(this::cleanExpiredEntries, 0, 10, TimeUnit.MINUTES);
    }

    private void loadConfig() {
        Properties config = new Properties();

        try (FileInputStream inputStream = new FileInputStream(fileConfig)) {
            config.load(inputStream);

            // Chargement des valeurs depuis le fichier .conf
            cacheExpirationTime = Long.parseLong(config.getProperty("expiration"));
            maxEntries = Integer.parseInt(config.getProperty("maxEntries"));
            maxCacheSize = Long.parseLong(config.getProperty("maxSize"));

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Error loading configuration file.");
        }
    }

    // Ajouter une entrée dans le cache
    public synchronized boolean add(String url, byte[] data, String contentType) {
        long dataSize = data.length;

        // Vérifier si l'entrée existe déjà
        if (cache.containsKey(url)) {
            System.out.println("L'entrée existe déjà dans le cache.");
            return false;
        }

        // Vérifier les limites avant d'ajouter
        if (currentCacheSize.get() + dataSize > maxCacheSize) {
            System.err.println("Ajout échoué : la taille du cache dépassera la limite (" + maxCacheSize + " octets).");
            return false;
        }

        if (cache.size() >= maxEntries) {
            System.err.println("Ajout échoué : le nombre maximal d'entrées dans le cache est atteint (" + maxEntries + ").");
            return false;
        }

        // Ajouter l'entrée dans le cache
        long expirationTime = System.currentTimeMillis() + cacheExpirationTime;
        CacheEntry cacheEntry = new CacheEntry(data, contentType, expirationTime);
        cache.put(url, cacheEntry);
        currentCacheSize.addAndGet(dataSize);
        System.out.println("Entrée ajoutée avec succès. Taille actuelle du cache : " + currentCacheSize.get() + " octets.");
        return true;
    }

    // Récupérer une entrée du cache si elle n'est pas expirée
    public CacheEntry get(String url) {
        CacheEntry cacheEntry = cache.get(url);
        if (cacheEntry != null && !cacheEntry.isExpired()) {
            return cacheEntry;
        }
        return null; // Cache miss ou cache expiré
    }

        // Supprimer une ou plusieurs entrées du cache en fonction d'une URL ou d'un motif
    public void remove(String pattern) {
        // Si le motif contient un caractère spécial (* ou ?), traiter comme un motif regex
        boolean isPattern = pattern.contains("*") || pattern.contains("?");
        String regexPattern = isPattern ? pattern.replace(".", "\\.").replace("*", ".*").replace("?", ".") : null;

        Iterator<Map.Entry<String, CacheEntry>> iterator = cache.entrySet().iterator();
        boolean found = false;

        while (iterator.hasNext()) {
            Map.Entry<String, CacheEntry> entry = iterator.next();
            String url = entry.getKey();

            // Si c'est un motif, comparer avec regex
            if (isPattern && url.matches(regexPattern)) {
                currentCacheSize.addAndGet(-entry.getValue().getData().length);
                iterator.remove();
                found = true;
                System.out.println("Entrée supprimée : " + url);
            } 
            // Sinon, comparer l'URL directement
            else if (!isPattern && url.equals(pattern)) {
                currentCacheSize.addAndGet(-entry.getValue().getData().length);
                iterator.remove();
                found = true;
                System.out.println("Entrée supprimée : " + url);
            }
        }

        if (!found) {
            System.out.println("Aucune entrée correspondante trouvée pour : " + pattern);
        } else {
            System.out.println("Taille actuelle du cache après suppression : " + currentCacheSize.get() + " octets.");
        }
    }


    // Vider tout le cache
    public void clear() {
        cache.clear();
        currentCacheSize.set(0);
    }

    // Nettoyer les entrées expirées
    private synchronized void cleanExpiredEntries() {
        Iterator<Map.Entry<String, CacheEntry>> iterator = cache.entrySet().iterator();
    
        while (iterator.hasNext()) {
            Map.Entry<String, CacheEntry> entry = iterator.next();
            if (entry.getValue().isExpired()) {
                currentCacheSize.addAndGet(-entry.getValue().getData().length);
                iterator.remove();
            }
        }
        System.out.println("Nettoyage effectué. Taille actuelle du cache : " + currentCacheSize.get() + " octets.");
    }
    
    public void showCacheContents() {
        if(currentCacheSize.get() == 0) {
            System.out.println("Aucun contenu dans le cache");
        } else {
            System.out.println("Contenu du cache :");
            for (Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
                System.out.println("URL : " + entry.getKey());
                System.out.println("  Taille : " + entry.getValue().getData().length + " octets");
                System.out.println("  Expiration : " + new Date(entry.getValue().getExpirationTime()));
            }
        }
        
    }

    // Arrêter le gestionnaire planifié
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }
    }
}