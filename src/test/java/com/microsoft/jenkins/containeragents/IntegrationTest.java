package com.microsoft.jenkins.containeragents;

import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebXmlConfiguration;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

import org.jvnet.hudson.test.ThreadPoolImpl;


import javax.servlet.ServletContext;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class IntegrationTest {

    static Logger LOGGER = Logger.getLogger(IntegrationTest.class.getName());

    @ClassRule
    public static JenkinsRule jenkinsRule = new JenkinsRule() {
        {
            timeout = -1;
        }

        @Override
        public URL getURL() throws IOException {
            return new URL("http://"+TestUtils.loadProperty("ACS_AGENT_TEST_JENKINS_URL", "localhost")+":"+localPort+contextPath+"/");
        }

        @Override
        protected ServletContext createWebServer() throws Exception {
            server = new Server(new ThreadPoolImpl(new ThreadPoolExecutor(10, 10, 10L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(),new ThreadFactory() {
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r);
                    t.setName("Jetty Thread Pool");
                    return t;
                }
            })));
            Method getExplodedDirMethod = Class.forName("org.jvnet.hudson.test.WarExploder").getDeclaredMethod("getExplodedDir", null);
            getExplodedDirMethod.setAccessible(true);
            WebAppContext context = new WebAppContext(((File) getExplodedDirMethod.invoke(null, null)).getPath(),contextPath);

            context.setClassLoader(getClass().getClassLoader());
            context.setConfigurations(new Configuration[]{new WebXmlConfiguration()});

            Constructor<?> webAppContextConstructor = Class.forName("org.jvnet.hudson.test.NoListenerConfiguration").getDeclaredConstructor(org.eclipse.jetty.webapp.WebAppContext.class);
            webAppContextConstructor.setAccessible(true);
            context.addBean(webAppContextConstructor.newInstance(context));

            server.setHandler(context);
            context.setMimeTypes(MIME_TYPES);
            context.getSecurityHandler().setLoginService(configureUserRealm());
            context.setResourceBase(((File) getExplodedDirMethod.invoke(null, null)).getPath());

            ServerConnector connector = new ServerConnector(server);
            HttpConfiguration config = connector.getConnectionFactory(HttpConnectionFactory.class).getHttpConfiguration();
            // use a bigger buffer as Stapler traces can get pretty large on deeply nested URL
            config.setRequestHeaderSize(12 * 1024);
            connector.setHost("0.0.0.0");
            if (System.getProperty("port")!=null)
                connector.setPort(Integer.parseInt(System.getProperty("port")));

            server.addConnector(connector);
            server.start();

            localPort = connector.getLocalPort();
            LOGGER.log(Level.INFO, "Running on {0}", getURL());

            return context.getServletContext();
        }

        @Override
        public void before() throws Throwable {
            super.before();
            jenkins.setSlaveAgentPort(60000);
            jenkins.save();
        }
    };

    @Rule
    public TemporaryFolder tmpFolder = new TemporaryFolder();

}
