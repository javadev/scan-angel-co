package com.github.javadev.scanangel;
	
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.phantomjs.PhantomJSDriver;
import org.openqa.selenium.phantomjs.PhantomJSDriverService;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class ScanAngel {
    private static final String URL_TO_LINKEDINCSV = "./urlToLinkedin.csv";
    private static String scanMode = "normal";
    private static List<Map<String, String>> urlToLinkedin = new ArrayList<>();

    public static void main(String ... args) throws Exception {
        try {
            urlToLinkedin = readUrlToLinkedin();
        } catch (FileNotFoundException ex) {
        }
        DesiredCapabilities cap = DesiredCapabilities.phantomjs();
        cap.setCapability(PhantomJSDriverService.PHANTOMJS_PAGE_SETTINGS_PREFIX + "userAgent",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/50.0.2661.75 Safari/537.36");
        cap.setCapability(PhantomJSDriverService.PHANTOMJS_PAGE_SETTINGS_PREFIX + "loadImages", false);
        cap.setCapability(PhantomJSDriverService.PHANTOMJS_PAGE_SETTINGS_PREFIX + "javascriptEnabled", true);
        cap.setBrowserName("chrome");
        cap.setVersion("42");  
        final WebDriver driver = new PhantomJSDriver(cap);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> driver.quit(), "Shutdown-thread"));

        List<String> urls = Arrays.asList("https://angel.co/sharing-economy-4/investors",
                "https://angel.co/social-network-2/investors",
                "https://angel.co/consumer-internet/investors",
                "https://angel.co/consumer/investors",
                "https://angel.co/real-estate-1/investors",
                "https://angel.co/rental-housing/investors",
                "https://angel.co/building-owners/investors");
        for (String url : urls) {
            List<Map<String, String>> urlData = downloadForUrl(driver, url);
            dataToXlsx(url, urlData);
        }
        generateCsv(URL_TO_LINKEDINCSV, urlToLinkedin);
        System.out.println(readFile(URL_TO_LINKEDINCSV));
        //Close the browser
        driver.quit();
    }

    private static void dataToXlsx(String url, List<Map<String, String>> data) throws IOException {
        String fileName = url.replace("https://angel.co/", "").replaceAll("[-/]","_");
        generateCsv(fileName + ".csv", data);
        csvToXlsx(fileName + ".csv", fileName + ".xlsx");
    }

    private static List<Map<String, String>> downloadForUrl(WebDriver driver, String url) throws Exception {
        driver.navigate().to(url);

        // Wait for the page to load, timeout after 20 seconds
        (new WebDriverWait(driver, 20)).until(ExpectedConditions.presenceOfElementLocated(
          By.xpath("//*[@id=\"root\"]/div[4]/div[2]/div[2]/div/div[2]/div[2]/div[1]/div/div[1]/div/div[2]/div[1]/a")));

        Integer amountOfInvestors = Integer.parseInt(driver.findElement(
          By.xpath("//*[@id=\"root\"]/div[4]/div[1]/div[2]/ul/li[2]/a")).getText().replaceAll("[^0-9]", ""));

System.out.println(url + ", amountOfInvestors - " + amountOfInvestors);
        driver.manage().timeouts().setScriptTimeout(10, java.util.concurrent.TimeUnit.SECONDS);
        List<Map<String, String>> data = new ArrayList<>();
        outer:
        for (int index = 0; index < amountOfInvestors / 25; index += 1) {
            Thread.sleep(100);
System.out.println(".");
            String page = downloadPage(driver, url, index + 1);
            org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(page);
            int emptyCount = 0;
            for (int index2 = 0; index2 < 25; index2 += 1) {
                Map<String, String> item = new LinkedHashMap<>();
                org.jsoup.nodes.Element element = doc.selectFirst("body > div > div:nth-child(" + index2 + ") > div > div.item.column > div > div.text > div.name > a");
                if (element == null) {
                    emptyCount += 1;
                    if (emptyCount == 2) {
                        break outer;
                    }
                    continue;
                }
                item.put("name", element.text());
                item.put("url", element.attr("href"));
                item.put("investments", doc.selectFirst("body > div > div:nth-child(" + index2 + ") > div > div.column.investments > div.value").text());
                item.put("company", doc.selectFirst("body > div > div:nth-child(" + index2 + ") > div > div.item.column > div > div.text > div.blurb").text());
                item.put("tags", doc.selectFirst("body > div > div:nth-child(" + index2 + ") > div > div.item.column > div > div.text > div.tags").text());
                item.put("linkedin", getLinkedIn(driver, element.attr("href")));
                item.put("email", getEmail(element.attr("href")));
                data.add(item);
System.out.println(item);
            }
        }
        return data;
    }

    private static String downloadPage(WebDriver driver, String url, int pageNumber) {
        return (String) ((JavascriptExecutor) driver).executeAsyncScript(
            "var url = arguments[0];" +
            "var callback = arguments[arguments.length - 1];" +
            "var xhr = new XMLHttpRequest();" +
            "xhr.open('GET', url, true);" +
            "var token = $('meta[name=\"csrf-token\"]').attr('content');" +
            "xhr.setRequestHeader('Accept', 'application/json, text/javascript, */*; q=0.01');"+
            "xhr.setRequestHeader('X-CSRF-Token', token);" +
            "xhr.setRequestHeader('X-Requested-With', 'XMLHttpRequest');"+
            "xhr.setRequestHeader('X-NewRelic-ID', 'UQICUFJaGwABVFRXAgc=');" +
            "xhr.withCredentials = true;" +
            "xhr.onreadystatechange = function() {" +
            "  if (xhr.readyState == 4) {" +
            "    callback(JSON.parse(xhr.responseText)['html']);" +
            "  }" +
            "};" +
            "xhr.send();", url + "?page=" + pageNumber);
    }

    private static String downloadProfile(WebDriver driver, String url) {
        return (String) ((JavascriptExecutor) driver).executeAsyncScript(
            "var url = arguments[0];" +
            "var callback = arguments[arguments.length - 1];" +
            "var xhr = new XMLHttpRequest();" +
            "xhr.open('GET', url, true);" +
            "xhr.setRequestHeader('Accept', 'text/html');"+
            "xhr.setRequestHeader('Accept-Encoding', 'gzip, deflate, br');"+
            "xhr.withCredentials = true;" +
            "xhr.onreadystatechange = function() {" +
            "  if (xhr.readyState == 4) {" +
            "    callback(xhr.responseText);" +
            "  }" +
            "};" +
            "xhr.send();", url);
    }

    private static String getEmail(String url) throws Exception {
        for (Map<String, String> item : urlToLinkedin) {
            if (item.get("url").equals(url)) {
                return item.get("email");
            }
        }
        return null;
    }

    private static String getLinkedIn(WebDriver driver, String url) throws Exception {
        for (Map<String, String> item : urlToLinkedin) {
            if (item.get("url").equals(url)) {
                return item.get("linkedin");
            }
        }
        if (!"normal".equals(scanMode)) {
            System.out.println("Skip load profile");
            return "SKIP";
        }
        Thread.sleep(3000);
        String profile = downloadProfile(driver, url);
        if (profile.contains("Error code: TBLKIP")) {
            scanMode = "BLOCKED";
            return "BLOCKED";
        }
        if (profile.contains("may have been made private or deleted")) {
            return "PRIVATE";
        }
        if (profile.contains("recaptcha")) {
            scanMode = "RECAPTCHA";
            return "RECAPTCHA";
        }
        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(profile);
        org.jsoup.nodes.Element element = doc.selectFirst("a[data-field=linkedin_url]");
        Map<String, String> item = new LinkedHashMap<>();
        item.put("url", url);
        item.put("linkedin", element == null ? null : ("".equals(element.attr("href").trim()) ? null : element.attr("href")));
        item.put("email", "null");
        urlToLinkedin.add(item);
        return element == null ? null : element.attr("href");
    }

    public static void csvToXlsx(String csvFileAddress, String xlsxFileAddress) {
        try {
            XSSFWorkbook workBook = new XSSFWorkbook();
            XSSFSheet sheet = workBook.createSheet("Sheet");
            int RowNum = 0;
            Reader in = new FileReader(csvFileAddress);
            Iterable<CSVRecord> records = CSVFormat.RFC4180.withFirstRecordAsHeader().parse(in);
            String header[] = new String[]{"Name", "Url", "Investments", "Company", "Tags", "Linkedin", "Email"};
            createRow(sheet, RowNum, header);
            for (CSVRecord record : records) {
                String str[] = new String[]{record.get("name"), record.get("url"),
                    record.get("investments"), record.get("company"), record.get("tags"), record.get("linkedin"), record.get("email")};
                RowNum++;
                createRow(sheet, RowNum, str);
            }
            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
            sheet.autoSizeColumn(2);
            sheet.autoSizeColumn(3);
            sheet.autoSizeColumn(4);
            sheet.autoSizeColumn(5);
            sheet.autoSizeColumn(6);
            try (FileOutputStream fileOutputStream = new FileOutputStream(xlsxFileAddress)) {
                workBook.write(fileOutputStream);
            }
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
    }

    private static void createRow(XSSFSheet sheet, int RowNum, String[] str) {
        XSSFRow currentRow=sheet.createRow(RowNum);
        for(int i=0;i<str.length;i++){
            currentRow.createCell(i).setCellValue(str[i]);
        }
    }

    private static void generateCsv(String fileName, List<Map<String, String>> data) throws IOException {
        if (data.isEmpty()) {
            return;
        }
        try (
            BufferedWriter writer = Files.newBufferedWriter(Paths.get(fileName));

            CSVPrinter csvPrinter = new CSVPrinter(writer, CSVFormat.DEFAULT
                    .withHeader(data.get(0).keySet().toArray(new String[]{})));
        ) {
            for (Map<String, String> item : data) {
                csvPrinter.printRecord(item.values());
            }
            csvPrinter.flush();
        }
    }

    private static List<Map<String, String>> readUrlToLinkedin() throws FileNotFoundException, IOException {
        List<Map<String, String>> result = new ArrayList<>();
        Reader reader = new FileReader(URL_TO_LINKEDINCSV);
        Iterable<CSVRecord> records = CSVFormat.RFC4180.withSkipHeaderRecord().withHeader("url", "linkedin", "email").parse(reader);
        for (CSVRecord record : records) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("url", record.get("url"));
            item.put("linkedin", record.get("linkedin"));
            item.put("email", (record.size() >= 3 ? record.get("email") : null));
            result.add(item);
        }
        return result;
    }

    private static String readFile(String path) throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(path));
        return new String(encoded, java.nio.charset.StandardCharsets.UTF_8);
    }
}
