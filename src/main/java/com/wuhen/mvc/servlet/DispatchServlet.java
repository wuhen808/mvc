package com.wuhen.mvc.servlet;


import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.wuhen.mvc.anno.Autowired;
import com.wuhen.mvc.anno.Controller;
import com.wuhen.mvc.anno.RequestMapping;
import com.wuhen.mvc.anno.Service;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class DispatchServlet extends HttpServlet {
    private List<String> classNames= Lists.newArrayList();
    private Map<String,Object> ioc= Maps.newHashMap();
    private List<Handler> handlers= Lists.newArrayList();


    private static  class Handler{
        private  String path;
        private  Method method;
        private  Object controller;

        public Handler(String path, Method method, Object controller) {
            this.path = path;
            this.method = method;
            this.controller = controller;
        }

        private boolean match(String url){
            return path.equals(url);
        }

    }
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doDispatch(req,resp);
    }

    private void doDispatch(HttpServletRequest req, HttpServletResponse resp) {

        Handler h = getHandler(req);
        if(null==h){
            try {
                resp.getWriter().println("404 NOT FOUND");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            h.method.invoke(h.controller,req,resp);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public Handler getHandler(HttpServletRequest req) {
        String url=req.getRequestURI().replace(req.getContextPath(),"");
        for (Handler handler : handlers) {
            if(handler.match(url)) {
                return handler;
            }
        }
        return null;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        System.out.println("=======================Init======================");
        System.out.println(this.getClass().getClassLoader());
        System.out.println(Thread.currentThread().getContextClassLoader());
       //1.初始化配置文件
        String pacakageName = loadConfig(config.getInitParameter("contextConfigLocation"));
        //2.扫描文件
        sacnner(pacakageName.replace(".","/"));
        //3 实例化注解标记对象
        instance();
        //4 自动注入
        autoWired();

        //5 初始化 handlerMappinp
        initHandlerMapping();
    }

    private void initHandlerMapping() {
        for (Map.Entry<String, Object> en : ioc.entrySet()) {
            Method[] declaredMethods = en.getValue().getClass().getDeclaredMethods();
            for (Method method : declaredMethods) {
                RequestMapping requestMapping=method.getAnnotation(RequestMapping.class);
                if(requestMapping!=null){
                    String path = requestMapping.value();
                    handlers.add(new Handler(path,method,en.getValue()));
                }
            }
        }
    }

    private void autoWired() {
        for (Map.Entry<String, Object> en : ioc.entrySet()) {
            //TODO 暂时不考虑父类
            Field[] declaredFields = en.getValue().getClass().getDeclaredFields();
            for (Field field : declaredFields) {
                Autowired annotation = field.getAnnotation(Autowired.class);
                if(annotation!=null){
                    field.setAccessible(true);
                    String value = annotation.value();
                    //TODO 暂不考虑同名不同类型 没找到。
                    try {
                        field.set(en.getValue(),ioc.get(value));
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void instance() {
        for (String className : classNames) {
            try {
                //class.forName() 会执行 static
                //classloader只会加载，newInstance 才会执行
                Class<?> aClass = Thread.currentThread().getContextClassLoader().loadClass(className);
                if(aClass.isAnnotationPresent(Controller.class)){
                    ioc.put(className,aClass.newInstance());
                }else if(aClass.isAnnotationPresent(Service.class)){
                    Service s=aClass.getAnnotation(Service.class);
                    ioc.put(s.value(),aClass.newInstance());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void sacnner(String packageName) {
        try {
            Enumeration<URL> urls = Thread.currentThread().getContextClassLoader().getResources(packageName);
            while (urls.hasMoreElements()){
                URL url = urls.nextElement();
                String protocol = url.getProtocol();
                if("file".equals(protocol)){
                    System.out.println("文件类型");
                    findClassByFile(URLDecoder.decode(url.getFile(), StandardCharsets.UTF_8.name()),packageName);
                }else if("jar".equals(protocol)){
                    System.out.println("jar文件");
                    JarFile jarFile = ((JarURLConnection) (url.openConnection())).getJarFile();
                    Enumeration<JarEntry> entries = jarFile.entries();
                    while (entries.hasMoreElements()){
                        JarEntry jarEntry = entries.nextElement();
                        String name=jarEntry.getName();
                        if(name.startsWith(packageName)&&name.endsWith(".class")){
                            classNames.add(name.replace("/",".").replace(".class",""));
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void findClassByFile(String path,String packageName) {
        File f=new File(path);
        if(!f.exists()||!f.isDirectory()) {
            classNames.add(packageName.replace("/",".")+"."+f.getName().replace(".class",""));
            return;
        }
        File[] files = f.listFiles(new FileFilter() {
            @Override
            public boolean accept(File file) {
                return file.isDirectory() || file.getName().endsWith(".class");
            }
        });
        for (File file : files) {
            if(file.isDirectory()){
                findClassByFile(path+"/"+file.getName(),packageName+"/"+file.getName());
            }else{
                classNames.add(packageName.replace("/",".")+"."+file.getName().replace(".class",""));
            }
        }

    }

    private String loadConfig(String config) {
        InputStream resourceAsStream = this.getClass().getClassLoader().getResourceAsStream(config);
        Properties p=new Properties();
        try {
            p.load(resourceAsStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return p.getProperty("packageName");
    }
}
