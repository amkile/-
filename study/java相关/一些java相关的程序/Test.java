import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import sun.misc.ProxyGenerator;

public class Test implements Runnable{

    static int i = 16;

    static {
        System.out.println("ok");
    }

    @Override
    public int hashCode() {
        return i<<1;
    }


    @Override
    public boolean equals(Object obj) {
        return false;
    }


    @Override
    public void run() {
        for (int i = 0; i < 10; i++) {
            int temp = 0;
            temp = t + 1;
            try {
                Thread.sleep(10);
            } catch (Exception e) {
                //TODO: handle exception
            }
            
            t = temp;
            System.out.println(Thread.currentThread().getName()+","+t);
        }
    }

    volatile int t = 0;
    Integer context = null;  
    boolean inited = false; 

    public void setStatus() {
        context = 1;  
        inited = true; 
    }


    public void dosome() throws Exception{
        //线程2:
        while(!inited){
            Thread.sleep(10);
        }
        context++;
    }


    public static void main(String[] args) throws Exception {
        // hashmap 相同元素判定
        // HashMap<Test,String> m = new HashMap<>(16);
        // // Class c = m.getClass();
        // // Field f = c.getField("table");
        // // f.setAccessible(true);
        // // Object i = f.getInt(m);
        // Test t = new Test();
        // // System.out.println(i);
        // for (int i = 0; i < 10; i++) {
        //     m.put(new Test(), "1");
        // }
        // System.out.println(m.size());

        // cpu缓存一致     
        // Test test = new Test();
        // Thread t = new Thread(new Runnable(){
        
        //     @Override
        //     public void run() {
        //         test.setStatus();
                
        //     }
        // });
        
        // Thread t2 = new Thread(new Runnable(){
        
        //     @Override
        //     public void run() {
        //         try {
        //             test.dosome();
        //         } catch (Exception e) {
        //             //TODO: handle exception
        //         } 
        //     }
        // });
        // t2.start();
        
        // t.start();

        // 代理 
        Target t = new Target();
        TargetInterface ti =(TargetInterface) Proxy.newProxyInstance(
            t.getClass().getClassLoader(), 
            t.getClass().getInterfaces(),
            new InvocationHandler(){
            
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    System.out.println("test");
                    return method.invoke(t, args);
                }
            } );
        ti.todo();
        byte[] classFile = ProxyGenerator.generateProxyClass("$Proxy1", Target.class.getInterfaces());
        try(FileOutputStream fos = new FileOutputStream("./Proxy.class")){
            fos.write(classFile);
            fos.flush();
            System.out.println("ok");
        }catch (Exception e) {
            //TODO: handle exception
        }
        System.out.println(t.getClass().getInterfaces().clone());
    }

}