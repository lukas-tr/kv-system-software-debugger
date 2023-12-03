public class Debugee {
    boolean field = true;
    String name = "Debugee";
    public static void main(String[] args) {
        printHello();
        int i = 1;
        Debugee d = new Debugee();
        int j = i * 2;
        for (int k = 0; k < 10; k++) {
            j += k;
        }
        System.out.println(j);
    }

    public static void printHello() {
        System.out.println("Hello World");
    }
}
