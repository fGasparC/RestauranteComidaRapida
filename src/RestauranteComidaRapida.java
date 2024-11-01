import java.util.Scanner;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RestauranteComidaRapida {
    //Colores para que la consola no sea tan caotica
    static final String CC = "\u001B[32m";
    static final String CP = "\u001B[34m";
    static final String CB = "\u001B[35m";
    static final String CR = "\u001B[31m";
    //Semaforos para las secciones criticas
    static final Semaphore[] SEMAFOROS_MOSTRADORES_COMIDA = {new Semaphore(1), new Semaphore(1)};
    static final Semaphore SEMAFORO_CLIENTES_RESTAURANTE = new Semaphore(1);
    static final Semaphore SEMAFORO_CLIENTES_CAJA = new Semaphore(1);
    static final Semaphore SEMAFORO_PIZZEROS_RESTAURANTE = new Semaphore(1);
    static final Semaphore SEMAFORO_BOCATEROS_RESTAURANTE = new Semaphore(1);
    //Variables globales constantes
    static final String[] PRODUCTOS = {"pizzas", "bocatas"};
    static final int[] PRECIO_PRODUCTOS = {12, 6};
    static final int[] COSTE_PRODUCTOS = {6, 3};
    //Variables compartidas Cocineros y Clientes
    static int[] mostradoresComida = {0, 0};
    //Variables compartidas Cocineros y Restaurante
    static int pizzas = 0;
    static int bocatas = 0;
    //Variables compartidads Restaurante y Clientes
    static int cajaRestaurante = 0;
    static int contadorClientes = 0;
    //Variables para detener a los "cocineros" y acabar el programa
    static boolean cierre = false;
    static int imprimirResumen = 0;

    //////////////////////////////////////////////
    static class Pizzero extends Thread {
        String[] acciones = {"Estoy estirando la masa", "Estoy poniendo los ingredientes", "Estoy cocinando la pizza"};
        int[] tiempos = {2, 1, 5};

        public Pizzero() {
        }

        @Override
        public void run() {
            while (!cierre) {
                //Prepara las pizzas, adquiere el semaforo y las añade a la variable global
                prepararPizzas();
                //Adquiere el semaforo del mostrador de pizzas y le añade una. Luego lo libera.
                ponerPizzaMostrador();
            }
            if (imprimirResumen < 1) imprimirResumen++;
            else {
                //Si es el ultimo en acabar llama al metodo re Restaurante que hace el resumen.
                resumenJornada();
            }
        }

        public void prepararPizzas() {
            for (int i = 0; i < acciones.length; i++) {
                try {
                    System.out.println(CP + "Pizzero: " + acciones[i]);
                    Thread.sleep(1000 * tiempos[i]);
                } catch (InterruptedException ex) {
                    Logger.getLogger(Pizzero.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            try {
                SEMAFORO_PIZZEROS_RESTAURANTE.acquire();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            pizzas++;
            SEMAFORO_PIZZEROS_RESTAURANTE.release();
            System.out.println(CP + "Pizzero: Ya he hecho " + pizzas + " pizzas");
        }

        public void ponerPizzaMostrador() {
            try {
                SEMAFOROS_MOSTRADORES_COMIDA[0].acquire();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            mostradoresComida[0]++;
            System.out.println(CP + "He puesto la pizza en el mostrador");
            System.out.println(CP + "Hay " + mostradoresComida[0] + " pizzas en el mostrador");
            SEMAFOROS_MOSTRADORES_COMIDA[0].release();
        }
    }

    ////////////////////////////////////////////////////
    static class Bocatero extends Thread {
        String[] acciones = {"Estoy cortando el pan",
                "Estoy poniendo mayonesa en el pan",
                "Estoy poniendo el resto de ingredientes",
                "Estoy envolviendo el bocadillo"};
        int[] tiempos = {1, 1, 2, 3};


        public Bocatero() {

        }

        @Override
        public void run() {

            while (!cierre) {
                //Prepara los bocadillos, adquiere el semaforo y lo añade a la variable global
                prepararBocatas();
                //Adquiere el semaforo del mostrador de bocatas y le añade uno. Luego lo libera.
                ponerBocatasMostrador();
            }
            if (imprimirResumen < 1) imprimirResumen++;
            else {
                //Si es el ultimo en acabar llama al metodo re Restaurante que hace el resumen.
                resumenJornada();
            }
        }

        public void prepararBocatas() {
            for (int i = 0; i < acciones.length; i++) {
                try {
                    System.out.println(CB + "Bocatero: " + acciones[i]);
                    Thread.sleep(1000 * tiempos[i]);
                } catch (InterruptedException ex) {
                    Logger.getLogger(Pizzero.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            try {
                SEMAFORO_BOCATEROS_RESTAURANTE.acquire();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            bocatas++;
            SEMAFORO_BOCATEROS_RESTAURANTE.release();
            System.out.println(CB + "Bocatero: Ya he hecho " + bocatas + " bocatas");
        }

        public void ponerBocatasMostrador() {
            try {
                SEMAFOROS_MOSTRADORES_COMIDA[1].acquire();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            mostradoresComida[1]++;
            System.out.println(CB + "Bocatero: He puesto el bocata en el mostrador");
            System.out.println(CB + "Hay " + mostradoresComida[1] + " bocatas en el mostrador");
            SEMAFOROS_MOSTRADORES_COMIDA[1].release();
        }
    }

    ////////////////////////////////////////////
    static class Cliente extends Thread {
        int numeroCliente;
        int decisionTipo;
        int decisionCantidad;
        int precioPedido;

        public Cliente(int numeroCliente) {
            this.numeroCliente = numeroCliente;
        }

        public void run() {
            try {
                //Entra, saluda y se duerme 10 segundos
                System.out.println(CC + "Cliente " + numeroCliente + ": Buenas tardes, voy a ver que pido. ");
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            //Toma la decision de si quiere pizza o bocata y la cantidad.
            decisionFinal();
            while (decisionCantidad > 0) {
                        /*
                        Si el mostrador tiene menos comida de la que quiere el cliente coge toda la que puede del mostrador, se descuenta
                        de la que queria y se duerme. Si puede coger toda la comida, la coge, paga y se va. Cada vez que un cliente se va
                        la clase Restaurante comprueba si siguen quedando clientes
                         */
                saquearMostrador();
            }
        }


        public int decidirPedido() {
            double aux = Math.random();
            int decision;
            if (aux > 0.5) decision = 0;
            else decision = 1;
            return decision;
        }

        public int decidirCantidad() {
            double aux = Math.random();
            if (aux >= 0 && aux < 0.2) return 1;
            else if (aux >= 0.2 && aux < 0.4) return 2;
            else if (aux >= 0.4 && aux < 0.6) return 3;
            else if (aux >= 0.6 && aux < 0.8) return 4;
            else return 5;
        }

        public void decisionFinal() {
            decisionTipo = decidirPedido();
            decisionCantidad = decidirCantidad();
            precioPedido = PRECIO_PRODUCTOS[decisionTipo] * decisionCantidad;
            System.out.println(CC + "Cliente " + numeroCliente + ": Quiero " + decisionCantidad + " " + PRODUCTOS[decisionTipo]);
        }

        public void increpar() {
            System.out.println(CC + "Cliente " + numeroCliente + ": ¡Daos prisa vagos! Quiero mis " + PRODUCTOS[decisionTipo]);
        }

        public void saquearMostrador() {
            try {
                SEMAFOROS_MOSTRADORES_COMIDA[decisionTipo].acquire();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            if (mostradoresComida[decisionTipo] - decisionCantidad < 0) {
                decisionCantidad = decisionCantidad - mostradoresComida[decisionTipo];
                mostradoresComida[decisionTipo] = 0;
                increpar();
                SEMAFOROS_MOSTRADORES_COMIDA[decisionTipo].release();
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            } else {
                mostradoresComida[decisionTipo] -= decisionCantidad;
                decisionCantidad = 0;
                SEMAFOROS_MOSTRADORES_COMIDA[decisionTipo].release();
                try {
                    SEMAFORO_CLIENTES_CAJA.acquire();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                cajaRestaurante += precioPedido;
                System.out.println(CC + "Cliente " + numeroCliente + ": Ya tengo todo mi pedido, me voy, me ha costado " + precioPedido + "€. ¡Muchas gracias!");
                SEMAFORO_CLIENTES_CAJA.release();
                try {
                    SEMAFORO_CLIENTES_RESTAURANTE.acquire();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                contadorClientes--;
                try {
                    comprobarCierre();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                SEMAFORO_CLIENTES_RESTAURANTE.release();
            }
        }
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.println("¿Cuantos clientes van a haber hoy?");
        contadorClientes = sc.nextInt();
        Cliente[] clientes = new Cliente[contadorClientes];
        Pizzero Luis = new Pizzero();
        Bocatero Manolo = new Bocatero();
        Luis.start();
        Manolo.start();
        for (int i = 0; i < contadorClientes; i++) {
            clientes[i] = new Cliente(i + 1);
            clientes[i].start();
        }
    }

    //Metodos de la clase Restaurante para mostrar el resumen y cerrar el restaurante si no quedan clientes
    public static void resumenJornada() {
        try {
            SEMAFORO_BOCATEROS_RESTAURANTE.acquire();
            SEMAFORO_PIZZEROS_RESTAURANTE.acquire();
            SEMAFORO_CLIENTES_CAJA.acquire();
            SEMAFORO_CLIENTES_RESTAURANTE.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        int gastos = ((pizzas * COSTE_PRODUCTOS[0]) + (bocatas * COSTE_PRODUCTOS[1]));
        System.out.println(CR + "\n\tRESUMEN JORNADA" +
                "\n-Pizzas cocinadas: " + pizzas +
                "\n-Pizzas sin vender: " + mostradoresComida[0] +
                "\n-Pizzas vendidas: " + (pizzas - mostradoresComida[0]) +
                "\n-Bocadillos preparados: " + bocatas +
                "\n-Bocadillos sin vender: " + mostradoresComida[1] +
                "\n-Bocadillos vendidos: " + (bocatas - mostradoresComida[1]) +
                "\n-Ingresos: " + cajaRestaurante + "€" +
                "\n-Gastos: " + gastos + "€" +
                "\n-Beneficios: " + (cajaRestaurante - gastos) + "€");
        SEMAFORO_BOCATEROS_RESTAURANTE.release();
        SEMAFORO_PIZZEROS_RESTAURANTE.release();
        SEMAFORO_CLIENTES_CAJA.release();
        SEMAFORO_CLIENTES_RESTAURANTE.release();
    }

    public static void comprobarCierre() throws InterruptedException {
        if (contadorClientes <= 0) {
            cierre = true;
        }
    }
}