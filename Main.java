package main;

import proxy.Service;

public class Main {
    public static void main(String[] args) {
        Service service = new Service();
        service.run();
        System.exit(0);
    }
   
}
