/**
 * Copyright 2012 Alexey Ragozin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gridkit.vicluster.telecontrol;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.gridkit.util.concurrent.FutureBox;
import org.gridkit.vicluster.telecontrol.bootstraper.Tunneller;
import org.gridkit.vicluster.telecontrol.bootstraper.TunnellerConnection;
import org.gridkit.vicluster.telecontrol.bootstraper.TunnellerConnection.ExecHandler;
import org.gridkit.vicluster.telecontrol.bootstraper.TunnellerConnection.FileHandler;
import org.gridkit.vicluster.telecontrol.bootstraper.TunnellerConnection.SocketHandler;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.rules.Timeout;

public class TunnellerProtocolTest {

	private InputStream masterIn;
	private OutputStream masterOut;
	
	private InputStream slaveIn;
	private OutputStream slaveOut;
	
	private Tunneller tunneler;
	private TunnellerConnection connection;
	
	@ClassRule
	public static Timeout timeout = new Timeout((int)TimeUnit.MINUTES.toMillis(10)); 
	
	@Before
	public void start() throws IOException, InterruptedException, TimeoutException {
		StreamPipe pipeA = new StreamPipe(64 << 10);
		StreamPipe pipeB = new StreamPipe(64 << 10);
		masterIn = new ISW("masterIn", pipeA.getInputStream());
		slaveOut = new OSW("slaveOut", pipeA.getOutputStream());
		masterOut = new OSW("masterOut", pipeB.getOutputStream());
		slaveIn = new ISW("slaveIn", pipeB.getInputStream());
		
		tunneler = new Tunneller();
		new Thread("Slave") {
			
			public void run() {
				tunneler.process(slaveIn, slaveOut);
			}
		}.start();
		connection = new TunnellerConnection("TEST", masterIn, masterOut, System.out, 1, TimeUnit.SECONDS);
		
//		TunnelTestHelper.enableChannelTrace(tunneler);
//		TunnelTestHelper.enableChannelTrace(connection);
	}
	
	@Test
	public void test_vanila_exec() throws InterruptedException, ExecutionException, IOException, TimeoutException {
		
		FutureBox<Integer> done = exec("echo", "Hallo welt!");
		
		assertEquals(Integer.valueOf(0), done.get());
	}

	@Test
	public void test_exec_with_stdErr() throws IOException, InterruptedException, ExecutionException, TimeoutException {
		
		FutureBox<Integer> done = execCmd("echo \"Hallo welt!\" 1>&2\n");
		
		assertEquals(Integer.valueOf(0), done.get());
		
	}

	@Test
	public void test_exec_with_redirect() throws IOException, InterruptedException, ExecutionException, TimeoutException {
		
		FutureBox<Integer> done = execCmd("echo \"Hallo welt!\"1> target/test.txt\n");
		
		assertEquals(Integer.valueOf(0), done.get());
		
	}

	@Test 
	public void test_exec_resource_leak() throws IOException, InterruptedException, ExecutionException, TimeoutException {

		List<Future<Integer>> futures = new ArrayList<Future<Integer>>();
		for(int i = 0; i != 1000; ++i) {
			futures.add(exec("echo", "exec-" + String.valueOf(i)));
			if (i > 4) {
				futures.remove(0).get();
			}
		}
		for(Future<Integer> f: futures) {
			assertEquals(Integer.valueOf(0), f.get());
		}		
	}

	private List<String> IGNORE_VARS = Arrays.asList("SHLVL", "OLDPWD");
	
	@Test 
	public void test_inherited_environment() throws IOException, InterruptedException, ExecutionException, TimeoutException {

		String cmd = isWindows() ? "echo VAR=%%%s%%\n" : "echo VAR=$%s\n";
		
		for(String var: System.getenv().keySet()) {
			if (isSafeVar(var) && !IGNORE_VARS.contains(var)) {
				String val = System.getenv(var);
				String c = String.format(cmd, var);
				Assert.assertEquals("Env[" + var + "]", val, matchLine("VAR=", captureCmdOut(c, null).get()));
			}
		}		
	}

	@Test 
	public void test_inherited_environment_empty_map() throws IOException, InterruptedException, ExecutionException, TimeoutException {
		
		String cmd = isWindows() ? "echo VAR=%%%s%%\n" : "echo VAR=$%s\n";
		
		for(String var: System.getenv().keySet()) {
			if (isSafeVar(var) && !IGNORE_VARS.contains(var)) {
				String val = System.getenv(var);
				String c = String.format(cmd, var);
				Assert.assertEquals("Env[" + var + "]", val, matchLine("VAR=", captureCmdOut(c, Collections.<String, String>emptyMap()).get()));
			}
		}		
	}

	@Test 
	public void test_override_path() throws IOException, InterruptedException, ExecutionException, TimeoutException {
		
		String cmd = isWindows() ? "echo VAR=%%%s%%\n" : "echo VAR=$%s\n";
		
		Map<String, String> vars = new HashMap<String, String>();
		String newPath = System.getenv("PATH") + File.pathSeparator + "..";
		vars.put("PATH", newPath);
		
		String c = String.format(cmd, "PATH");
		Assert.assertEquals(newPath, matchLine("VAR=", captureCmdOut(c, vars).get()));
	}

	@Test 
	public void test_set_env_var() throws IOException, InterruptedException, ExecutionException, TimeoutException {
		
		String cmd = isWindows() ? "echo VAR=%%%s%%\n" : "echo VAR=$%s\n";
		
		Map<String, String> vars = new HashMap<String, String>();
		vars.put("NEWVAR", "random value");
		
		String c = String.format(cmd, "NEWVAR");
		Assert.assertEquals("random value", matchLine("VAR=", captureCmdOut(c, vars).get()));
	}

	@Test 
	public void test_env_var_override() throws IOException, InterruptedException, ExecutionException, TimeoutException {
		
		String cmd = isWindows() ? "echo VAR=%%%s%%\n" : "echo VAR=$%s\n";
		
		Map<String, String> vars = new HashMap<String, String>();
		vars.put("PROMT", "test");
		
		String c = String.format(cmd, "PROMT");
		Assert.assertEquals("PROMT expected to be overriden", "test", matchLine("VAR=", captureCmdOut(c, vars).get()));
	}

	@Test 
	public void test_env_var_remove() throws IOException, InterruptedException, ExecutionException, TimeoutException {
		
		String cmd = isWindows() ? "echo VAR=%%%s%%\n" : "echo VAR=$%s\n";
		
		String var = isWindows() ? "HOMEPATH" : "HOME";		
		
		Map<String, String> vars = new HashMap<String, String>();
		vars.put(var, null);
		
		String c = String.format(cmd, var);
		if (isWindows()) {
			Assert.assertEquals(var + " expected to be erased", "%" + var + "%", matchLine("VAR=", captureCmdOut(c, vars).get()));
		}
		else {
			Assert.assertEquals(var + " expected to be erased","", matchLine("VAR=", captureCmdOut(c, vars).get()));
		}
	}
	
	private boolean isSafeVar(String var) {
		for(int i = 0; i != var.length(); ++i) {
			char ch = var.charAt(i);
			if (!Character.isJavaIdentifierPart(ch)) {
				return false;
			}
		}
		return true;
	}

	private String matchLine(String prefix, String text) {
		String[] line = text.split("[\\r\\n]+");
		for(String l: line) {
			if (l.startsWith(prefix)) {
				return l.substring(prefix.length());
			}
		}
		return text;		
	}
	
	@Test @Ignore("it has never worked, ugly java sockets to blame")
	public void test_bind() throws IOException, InterruptedException, ExecutionException, TimeoutException {
		
		final FutureBox<SocketAddress> bind = new FutureBox<SocketAddress>();
		
		connection.newSocket(new SocketHandler() {
			
			@Override
			public void bound(String host, int port) {
				bind.setData(new InetSocketAddress(host, port));
			}
			
			@Override
			public void accepted(String rhost, int rport, InputStream soIn, OutputStream soOut) {
				System.out.println("Accepted [" + rhost + ": " + rport + "]");
				try {
					soOut.write("Pong!\n".getBytes());
					StreamHelper.copy(soIn, System.out);
					soIn.close();
					soOut.close();
					System.out.println("Remote socket closed");
				} catch (IOException e) {
					e.printStackTrace();
				}				
			}
		});
		
		SocketAddress sa = bind.get();
		System.out.println("Bound socket: " + sa);
		Socket sock = new Socket();
		sock.connect(sa);
		sock.getOutputStream().write("Ping!\n".getBytes());
//		sock.shutdownOutput();
		Thread.sleep(500);
		StreamHelper.copyAvailable(sock.getInputStream(), System.err);
		Thread.sleep(500);
		sock.close();
		System.out.println("Socket closed");
		
		Assert.assertTrue(sock.isClosed());
	}
	
	@Test(timeout = 30000)
	public void test_vanila_file_push() throws IOException, InterruptedException {

		TextWriter writer = new TextWriter();
		String path = "target/test-file.dat";
		File tfile = new File(path);
		deleteAll(tfile);

		Assert.assertFalse(tfile.exists());
		
		connection.pushFile(path, writer);
		
		writer.done.await();
		
		Assert.assertTrue(tfile.exists());
		Assert.assertEquals(null, writer.error);
		Assert.assertEquals(tfile.length(), writer.size);
		Assert.assertEquals(tfile.getAbsolutePath(), writer.rpath);
	}

	@Test(timeout = 30000)
	public void test_user_home_file_push() throws IOException, InterruptedException {
		
		TextWriter writer = new TextWriter();
		String path = "~/.nanocloud-test/test-file.dat";
		File tfile = new File(new File(System.getProperty("user.home")), path.substring(2));
		deleteAll(tfile);
		
		Assert.assertFalse(tfile.exists());
		
		connection.pushFile(path, writer);
		
		writer.done.await();
		
		Assert.assertTrue(tfile.exists());
		Assert.assertEquals(null, writer.error);
		Assert.assertEquals(tfile.length(), writer.size);
		Assert.assertEquals(tfile.getCanonicalPath(), writer.rpath);
	}

	@Test(timeout = 30000)
	public void test_temp_dir_file_push() throws IOException, InterruptedException {
		
		TextWriter writer = new TextWriter();
		String path = "{tmp}/.nanocloud-test/test-file.dat";
		File tfile = new File(File.createTempFile("nanotest", "").getParentFile(), path.substring(6));
		deleteAll(tfile);
		
		Assert.assertFalse(tfile.exists());
		
		connection.pushFile(path, writer);
		
		writer.done.await();
		
		Assert.assertEquals(null, writer.error);
		Assert.assertTrue(tfile.exists());
		Assert.assertEquals(tfile.length(), writer.size);
		Assert.assertEquals(tfile.getCanonicalPath(), writer.rpath);
	}
	
	@Test(timeout = 30000)
	public void test_mkdirs_on_file_push() throws IOException, InterruptedException {
		
		TextWriter writer = new TextWriter();
		String path = "target/file-cache/test-file.dat";
		File tfile = new File(path);
		deleteAll(tfile.getParentFile());
		
		Assert.assertFalse(tfile.getParentFile().exists());
		
		connection.pushFile(path, writer);
		
		writer.done.await();
		
		Assert.assertTrue(tfile.exists());
		Assert.assertEquals(null, writer.error);
		Assert.assertEquals(tfile.length(), writer.size);
		Assert.assertEquals(tfile.getAbsolutePath(), writer.rpath);
	}

	@Test(timeout = 30000)
	public void test_no_override() throws IOException, InterruptedException {
		
		TextWriter writer = new TextWriter();
		String path = "target/test-file.dat";
		File tfile = new File(path);
		deleteAll(tfile);
		
		Assert.assertFalse(tfile.exists());
		
		FileOutputStream fos = new FileOutputStream(tfile);
		fos.write("TEST".getBytes());
		fos.close();
		
		connection.pushFile(path, writer);
		
		writer.done.await();
		
		Assert.assertTrue(tfile.exists());
		Assert.assertFalse(writer.written);
		Assert.assertEquals(null, writer.error);
		Assert.assertEquals(4, writer.size);
		Assert.assertEquals(tfile.getAbsolutePath(), writer.rpath);
	}

	@Test(timeout = 30000)
	public void test_error_handling() throws IOException, InterruptedException {
		
		TextWriter writer = new TextWriter();
		String path = "target/test-file.dat";
		File tfile = new File(path);
		deleteAll(tfile);
		
		Assert.assertFalse(tfile.exists());

		tfile.mkdirs();
		
		connection.pushFile(path, writer);
		
		writer.done.await();
		
		Assert.assertTrue(tfile.exists());
		Assert.assertFalse(writer.written);
		Assert.assertEquals("Target path is directory", writer.error);
	}

	@Test(timeout = 30000)
	public void test_error_handling2() throws IOException, InterruptedException {
		
		String path = "target/test-file.dat";
		final File tfile = new File(path);
		deleteAll(tfile);

		TextWriter writer = new TextWriter() {

			@Override
			public void accepted(OutputStream out) {
				tfile.mkdirs();
				super.accepted(out);
			}
		};
		
		Assert.assertFalse(tfile.exists());
		
		connection.pushFile(path, writer);
		
		writer.done.await();
		
		Assert.assertTrue(tfile.exists());
		Assert.assertTrue(writer.written);
		Assert.assertEquals("Failed to rename target file", writer.error);
	}

	@Test(timeout = 30000)
	public void test_error_handling3() throws IOException, InterruptedException {
		
		String path = "target/file-cache/test-file.dat";
		final File tfile = new File(path);
		deleteAll(tfile.getParentFile());

		FileOutputStream fos = new FileOutputStream(tfile.getParentFile());
		fos.close();
		
		TextWriter writer = new TextWriter();		
		Assert.assertFalse(tfile.exists());
		
		connection.pushFile(path, writer);
		
		writer.done.await();
		
		Assert.assertFalse(tfile.exists());
		Assert.assertTrue(writer.error.startsWith("java.io.IOException:"));
	}
	
	@Test(timeout = 30000)
	public void test_concurrent_write_handling() throws IOException, InterruptedException {
		
		String path = "target/test-file.dat";
		final File tfile = new File(path);
		deleteAll(tfile);
		
		TextWriter writer = new TextWriter() {
			
			@Override
			public void accepted(OutputStream out) {
				try {
					FileOutputStream fos = new FileOutputStream(tfile);
					fos.write("TEST".getBytes());
					fos.close();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
				super.accepted(out);
			}
			
		};
		
		Assert.assertFalse(tfile.exists());
		
		connection.pushFile(path, writer);
		
		writer.done.await();
		
		Assert.assertTrue(tfile.exists());
		Assert.assertTrue(writer.written);
		Assert.assertEquals(null, writer.error);
		Assert.assertEquals(4, writer.size);
	}
	
	private void deleteAll(File path) {
		File[] clist = path.listFiles();
		if (clist != null) {
			for(File c : clist) {
				if (c.isDirectory()) {
					deleteAll(c);
				}
				else {
					c.delete();
				}
			}
		}
		path.delete();
	}
	
	private static class TextWriter extends Thread implements FileHandler {

		int repeat = 100;
		String rpath;
		String error;
		long size = -1;
		boolean written = false;
		CountDownLatch done = new CountDownLatch(1);
		
		OutputStream out;
		
		@Override
		public void run() {
			try {
				for(int i = 0; i != repeat; ++i) {
					out.write("Tunneller test file\n".getBytes());
				}
				out.close();
			} catch (IOException e) {
				error = e.toString();
				done.countDown();
			}
		}

		@Override
		public void accepted(OutputStream out) {
			this.out = out;
			written = true;
			this.start();
		}

		@Override
		public void confirmed(String path, long size) {
			rpath = path;
			this.size = size;
			done.countDown();
		}

		@Override
		public void failed(String path, String error) {
			rpath = path;
			this.error = error;
			done.countDown();
		}
	}
	
	private String[] ccat(String s, String... ss) {
	    String[] p = s.split("\\s+");
	    String[] r = new String[p.length + ss.length];
	    int n = 0;
	    for(String sp: p) {
	        r[n++] = sp;
	    }
	    for(String se: ss) {
	        r[n++] = se;
	    }
	    return r;
	}
	
	private FutureBox<Integer> exec(String... cmd) throws IOException {
		final FutureBox<Integer> done = new FutureBox<Integer>();
		
		if (isWindows()) {
		    cmd = ccat("cmd /c", cmd);
		}
		
		connection.exec(".", cmd, null, new ExecHandler() {
			
			InputStream stdOut;
			InputStream stdErr;
			
			@Override
			public void started(OutputStream stdIn, InputStream stdOut, InputStream stdErr) {
				try {
					stdIn.close();
				} catch (IOException e) {
					// ignore
				}
				this.stdOut = stdOut;
				this.stdErr = stdErr;
			}
			
			@Override
			public void finished(int exitCode) {
				try {
				    System.out.println("Exit code " + exitCode);
				    if (stdOut != null) {
				        StreamHelper.copy(stdOut, System.out);
				    }
				    if (stdErr != null) {
				        StreamHelper.copy(stdErr, System.err);
				    }
					done.setData(exitCode);
				} catch (IOException e) {
					done.setError(e);
				}
			}
		});
		return done;
	}

	@SuppressWarnings("unused")
	private FutureBox<String> execOutputCapture(Cmd cmd) throws IOException {
		final FutureBox<String> done = new FutureBox<String>();
		
		final ByteArrayOutputStream capture = new ByteArrayOutputStream();
		
		connection.exec(".", cmd.commands, cmd.vars, new ExecHandler() {
			
			InputStream stdOut;
			InputStream stdErr;
			
			@Override
			public void started(OutputStream stdIn, InputStream stdOut, InputStream stdErr) {
				try {
					stdIn.close();
				} catch (IOException e) {
					// ingore
				}
				this.stdOut = stdOut;
				this.stdErr = stdErr;
			}
			
			@Override
			public void finished(int exitCode) {
				try {
					System.out.println("Exit code " + exitCode);
					StreamHelper.copy(stdOut, capture);
					StreamHelper.copy(stdErr, System.err);
					done.setData(new String(capture.toByteArray()));
				} catch (Exception e) {
					done.setError(e);
				}
			}
		});
		return done;
	}
	
	private FutureBox<Integer> execCmd(final String cmd) throws IOException {
		final FutureBox<Integer> done = new FutureBox<Integer>();
		
		String sh = isWindows() ? "cmd" : "sh";
		
		connection.exec(".", new String[]{sh}, null, new ExecHandler() {
			
			InputStream stdOut;
			InputStream stdErr;
			
			@Override
			public void started(OutputStream stdIn, InputStream stdOut, InputStream stdErr) {
				System.out.println("Started");
				try {					
					stdIn.write(cmd.getBytes());
					stdIn.close();
				} catch (IOException e) {
					// ingore
				}
				this.stdOut = stdOut;
				this.stdErr = stdErr;
			}
			
			@Override
			public void finished(int exitCode) {
                try {
                    System.out.println("Exit code " + exitCode);
                    if (stdOut != null) {
                        StreamHelper.copy(stdOut, System.out);
                    }
                    if (stdErr != null) {
                        StreamHelper.copy(stdErr, System.err);
                    }
                    done.setData(exitCode);
                } catch (IOException e) {
                    done.setError(e);
                }
			}
		});
		return done;
	}

	private FutureBox<String> captureCmdOut(final String cmd, Map<String, String> env) throws IOException {
		final FutureBox<String> done = new FutureBox<String>();
		
		String[] sh = isWindows() ? new String[]{"cmd"} : new String[]{"sh"};
		
		connection.exec(".", sh, env, new ExecHandler() {
			
			InputStream stdOut;
			InputStream stdErr;
			
			@Override
			public void started(OutputStream stdIn, InputStream stdOut, InputStream stdErr) {
				System.out.println("Started");
				try {
					stdIn.write(cmd.getBytes());
					stdIn.close();
				} catch (IOException e) {
					// ingore
				}
				this.stdOut = stdOut;
				this.stdErr = stdErr;
			}
			
			@Override
			public void finished(int exitCode) {
				try {
					System.out.println("Exit code " + exitCode);
					ByteArrayOutputStream bos = new ByteArrayOutputStream();
					StreamHelper.copy(stdOut, bos);
					StreamHelper.copy(stdErr, System.err);
					done.setData(new String(bos.toByteArray()));
				} catch (Exception e) {
					done.setError(e);
				}
			}
		});
		return done;
	}

	private boolean isWindows() {
		return ManagementFactory.getOperatingSystemMXBean().getName().toLowerCase().startsWith("windows");
	}

	@SuppressWarnings("unused")
	private FutureBox<Void> execCat(final String cmd) throws IOException {
		final FutureBox<Void> done = new FutureBox<Void>();
		
		String sh = "cat";
		
		connection.exec(".", new String[]{sh}, null, new ExecHandler() {
			
			InputStream stdOut;
			InputStream stdErr;
			
			@Override
			public void started(OutputStream stdIn, InputStream stdOut, InputStream stdErr) {
				System.out.println("Started");
				try {
					stdIn.write(cmd.getBytes());
					stdIn.close();
				} catch (IOException e) {
					// ingore
				}
				this.stdOut = stdOut;
				this.stdErr = stdErr;
			}
			
			@Override
			public void finished(int exitCode) {
				try {
					StreamHelper.copy(stdOut, System.out);
					StreamHelper.copy(stdErr, System.err);
					System.out.println("Exit code " + exitCode);
					done.setData(null);
				} catch (IOException e) {
					done.setError(e);
				}
			}
		});
		return done;
	}
	
	private static class OSW extends OutputStream {
		
		private String name;
		private OutputStream delegate;
		
		public OSW(String name, OutputStream delegate) {
			this.name = name;
			this.delegate = delegate;
		}

		public void write(int b) throws IOException {
			delegate.write(b);
		}

		public void write(byte[] b) throws IOException {
			delegate.write(b);
		}

		public void write(byte[] b, int off, int len) throws IOException {
			delegate.write(b, off, len);
		}

		public void flush() throws IOException {
			delegate.flush();
		}

		public void close() throws IOException {
			delegate.close();
			System.out.println("Closed stream: " + name);
		}

		public String toString() {
			return delegate.toString();
		}
	}
	
	private static class ISW extends InputStream {
		
		private String name;
		private InputStream delegate;
		
		public ISW(String name, InputStream delegate) {
			this.name = name;
			this.delegate = delegate;
		}

		public int read() throws IOException {
			return delegate.read();
		}

		public int read(byte[] b) throws IOException {
			return delegate.read(b);
		}

		public int read(byte[] b, int off, int len) throws IOException {
			return delegate.read(b, off, len);
		}

		public long skip(long n) throws IOException {
			return delegate.skip(n);
		}

		public int available() throws IOException {
			return delegate.available();
		}

		public String toString() {
			return delegate.toString();
		}

		public void close() throws IOException {
			delegate.close();
			System.out.println("Closed stream: " + name);
		}

		public void mark(int readlimit) {
			delegate.mark(readlimit);
		}

		public void reset() throws IOException {
			delegate.reset();
		}

		public boolean markSupported() {
			return delegate.markSupported();
		}
	}
	
	@SuppressWarnings("unused")
	private static Cmd cmd(String... commands) {
		Cmd c = new Cmd();
		c.commands = commands;
		return c;
	}
	
	private static class Cmd {
		
		String[] commands;
		Map<String, String> vars = new HashMap<String, String>();
		
	}
}
