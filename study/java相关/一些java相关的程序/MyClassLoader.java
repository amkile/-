public class MyClassLoader extends ClassLoader{
    public static void main(String[] args) throws Exception{
        Class c =  MyClassLoader.class.getClassLoader().loadClass("Test");
        c.newInstance();
        System.out.println(MyClassLoader.class.getClassLoader());
        // String s = new String();
        // s.toSting();
        // System.out.println(System.getProperty("sun.boot.class.path"));
    }
}