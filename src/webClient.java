
import java.io.*;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;

import java.nio.charset.StandardCharsets;



public class webClient {

    public static void main(String[] args) {
        if (args.length < 1)
            return;
        for (String arg : args) {

            Thread x = new Thread(() -> {
                String hostName;
                String pathName;
                String fileName;
                URL url;
                try {
                    url = new URL(arg);
                    hostName = url.getHost();
                    pathName = getPathName(url);
                    fileName = getFileName(url);
                } catch (MalformedURLException x1) {
                    System.out.println("Malformed URL Exception raised");
                    return;
                }

                try {
                    Socket socket = new Socket(hostName, 80);

                    InputStream is = socket.getInputStream();
                    OutputStream os = socket.getOutputStream();

                    PrintWriter pw = new PrintWriter(os);
                    BufferedInputStream bis = new BufferedInputStream(is);

                    pw.print("GET " + pathName + " HTTP/1.1\r\n");
                    pw.print("Host: " + hostName + "\r\n");
                    pw.print("Connection: Keep-alive\r\n");
                    pw.print("\r\n");
                    pw.flush();

                    File file = new File(hostName + "_" + fileName);
                    if (isFolder(fileName)) {
                        if (!file.mkdir()) {
                            System.out.println("Create file failed !!!");
                            System.exit(1);
                        } else {
                            saveFolder(pw, bis, url, file);
                        }
                    } else {
                        FileOutputStream fOut = new FileOutputStream(file);
                        saveFile(bis, fOut);
                    }
                } catch (IOException e) {
                    System.err.println("Exception: " + e);
                    System.exit(1);
                }
            });
            x.start();
        }
    }

    public static int checkString(String x)
    {
        if (x.contains("\r\n\r\n"))
        {
            return 3;
        }
        if (x.contains("Content-Length: "))
        {
            return 1;
        }

        if (x.contains("Transfer-Encoding: chunked"))
        {
            return 2;
        }

        return 0;
    }

    public static int getContentLength(String x) {
        String[] line=x.split("\r\n");
        for (String w:line)
        {
            String[] dataLine=w.split(": ");
            if (dataLine[0].equals("Content-Length")) {
                return Integer.parseInt(dataLine[1]);
            }
        }
        return 0;
    }

    public static boolean checkLastChunk(String x) {
        return x.equals("\r\n");
    }

    public static boolean checkSpace(String x) {
        return x.contains("\r\n");
    }
    public static int getSizeChunk(String x) {
        String[] data=x.split("\r\n");
        return Integer.parseInt(data[0],16);
    }
    public static String getPathName(URL url) {
        String pathName=url.getPath();
        if (pathName.equals("")) {
            pathName="/";
        }
        return pathName;
    }
    public static String getFileName(URL url) {
        String fileName;
        String pathName=getPathName(url);
        if (pathName.equals("/")) {
            fileName = "index.html";
            return fileName;
        }
        else if ((pathName.charAt(pathName.length()-1))!='/') {
            if (!pathName.contains(".")) {
                fileName="index.html";
                return fileName;
            }
        }

        String[] word = url.toString().split("/");
        fileName = word[word.length - 1];

        return fileName;
    }
    public static boolean isFolder(String fileName) {
        return !fileName.contains(".");
    }
    public static void saveFile(BufferedInputStream bis,FileOutputStream fOut) {
        boolean useChunk = false;
        boolean useLength = false;
        int contentLength;
        byte[] bytes=new byte[1];
        String data="";
        int size;
        try {
            while ((size = bis.read(bytes)) != -1) {
                String x = new String(bytes, StandardCharsets.UTF_8);
                data = data.concat(x);
                int z = checkString(data);
                if (!useChunk && !useLength) {

                    if (z == 1) {
                        useLength = true;
                    } else if (z == 2) {
                        useChunk = true;
                    }
                }

                if (z == 3) {
                    System.out.println(data);
                    //fOut.write(data.getBytes(StandardCharsets.UTF_8));
                    break;
                }
            }
        }
        catch (IOException e){
            System.out.println(e);
            System.exit(1);
        }
        if (useChunk)
        {
            saveFileChunked(bis,fOut);
        }
        else
        {
            contentLength=getContentLength(data);
            saveFileLength(bis,fOut,contentLength);
        }
    }

    public static void saveFileLength(BufferedInputStream bis, FileOutputStream fOut,int contentLength) {
        int sumLength = 0;
        byte[] byte1 = new byte[2048*2048];
        int size;
        String data = "";
        System.out.println("Downloading....");
        try {
            while ((size = bis.read(byte1)) != -1) {
                //String x=new String(byte1,StandardCharsets.UTF_8);
                //data=data.concat(x);
                sumLength = sumLength + size;
                fOut.write(byte1, 0, size);
                System.out.println("Byte is received");
                if (sumLength == contentLength) {
                    //sSystem.out.println(data);
                    break;
                }
            }
            System.out.println("Download file successfully");
        } catch (IOException e) {
            System.out.println(e);
            System.exit(1);
        }
    }

    public static void saveFileChunked(BufferedInputStream bis,FileOutputStream fOut) {
        int sumChunk = 0;
        int sizeChunk = -1;
        boolean isCountChunk = false;
        boolean lastFile = false;

        byte[] bytes = new byte[1];
        String data = "";
        int size;
        System.out.println("Downloading....");
        try {
            while ((size = bis.read(bytes)) != -1) {
                String x = new String(bytes, StandardCharsets.UTF_8);
                data = data.concat(x);

                if (isCountChunk) {
                    sumChunk++;
                    if (sumChunk - 2 == sizeChunk) {
                        if (checkLastChunk(data) && lastFile) {
                            System.out.println("Download file successfully");
                            break;
                        }
                        fOut.write(data.getBytes(StandardCharsets.UTF_8));
                        System.out.println("Chunked is received");
                        sizeChunk = -1;
                        sumChunk = 0;
                        isCountChunk = false;
                        data = "";


                    }
                } else {
                    if (data.equals("0\r\n")) {
                        lastFile = true;
                    }
                    if (checkSpace(data)) {
                        isCountChunk = true;
                        sizeChunk = getSizeChunk(data);
                        data = "";
                    }

                }

            }
        } catch (IOException e) {
            System.out.println(e);
            System.exit(1);
        }
    }
    public static boolean checkFileDownload(String x) {
        return x.contains("\"") ;
    }
    public static boolean checkHref(String x) {
        return x.contains("href=\"");
    }

    public static String getNameFileDownload(String x) {
        String[] name=x.split("\"");
        return name[0];
    }
    public static void saveFileSubfolder(PrintWriter pw,BufferedInputStream bis,int contentLength,URL url,File file){
        int size;
        byte[] bytes=new byte[1];
        int sum=0;
        String data="";
        String[] nameFile=new String[100];
        int i=0;
        boolean href=false;
        try {
            while ((size=bis.read(bytes))!=-1) {
                sum++;
                String x = new String(bytes, StandardCharsets.UTF_8);
                data = data.concat(x);
                if (checkHref(data)) {
                    data="";
                    href=true;
                }
                if (href) {
                    if (checkFileDownload(data)) {
                        if (data.contains(".")) {
                            nameFile[i] = getNameFileDownload(data);
                            data = "";
                            href = false;
                            i++;
                        }
                        else
                        {
                            data="";
                            href=false;
                        }
                    }
                }

                if (sum==contentLength)
                {
                    break;
                }
            }
        }
        catch (IOException e)
        {
            System.out.println(e);
            System.exit(1);
        }
        String currentDirectory = System.getProperty("user.dir");
        for (String w:nameFile) {
            try {
                if (w != null) {
                    sendRequest(pw, w, url);
                    FileOutputStream out = new FileOutputStream(currentDirectory + "/" + file.toString() + "/" + file.toString() + "_" + w);
                    saveFile(bis, out);
                }
            }
            catch (FileNotFoundException e)
            {
                System.out.println(e);
                System.exit(1);
            }
        }
        System.out.println("Download all file successfully");

    }
    public static void sendRequest(PrintWriter pw,String w,URL url){
        String hostName = url.getHost();
        String pathName = getPathName(url);
        String fileName=getFileName(url);

        pw.print("GET " + pathName+w + " HTTP/1.1\r\n");
        pw.print("Host: " + hostName + "\r\n");
        pw.print("Connection: Keep-alive\r\n");
        pw.print("\r\n");
        pw.flush();
    }
    public static void saveFolder(PrintWriter pw, BufferedInputStream bis,URL url,File file) {
        boolean useChunk = false;
        boolean useLength = false;
        int contentLength;
        byte[] bytes=new byte[1];
        String data="";
        int size;

        try {
            while ((size = bis.read(bytes)) != -1) {
                String x = new String(bytes, StandardCharsets.UTF_8);
                data = data.concat(x);
                int z = checkString(data);
                if (!useChunk && !useLength) {

                    if (z == 1) {
                        useLength = true;
                    } else if (z == 2) {
                        useChunk = true;
                    }
                }

                if (z == 3) {
                    System.out.println(data);
                    break;
                }
            }
        }
        catch (IOException e){
            System.out.println(e);
            System.exit(1);
        }

        contentLength=getContentLength(data);
        saveFileSubfolder(pw,bis,contentLength,url,file);

    }

}