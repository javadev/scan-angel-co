package com.github.javadev.scanangel;
	
import java.util.*;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.phantomjs.PhantomJSDriver;
import org.openqa.selenium.phantomjs.PhantomJSDriverService;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class ScanAngel {
    public static void main(String ... args) throws Exception {
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
            dataToXlsx(urlData);
        }
        //Close the browser
        driver.quit();
    }

    private static void dataToXlsx(List<Map<String, String>> data) {
    }

    private static List<Map<String, String>> downloadForUrl(WebDriver driver, String url) throws Exception {
        driver.navigate().to(url);

        // Wait for the page to load, timeout after 10 seconds
        (new WebDriverWait(driver, 10)).until(ExpectedConditions.presenceOfElementLocated(
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

    private static String getLinkedIn(WebDriver driver, String url) throws Exception {
        Thread.sleep(300);
        String profile = downloadProfile(driver, url);
        if (profile.contains("Error code: TBLKIP")) {
            return "BLOCKED";
        }
        if (profile.contains("may have been made private or deleted")) {
            return "PRIVATE";
        }
        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(profile);
        org.jsoup.nodes.Element element = doc.selectFirst("a[data-field=linkedin_url]");
        return element == null ? null : element.attr("href");
    }
}
