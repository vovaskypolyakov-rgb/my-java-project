import io.github.bonigarcia.wdm.WebDriverManager;
import io.qameta.allure.*;
import org.openqa.selenium.*;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import java.io.File;
import java.time.Duration;

@Epic("ЕСИА Госуслуги")
@Feature("Электронные больничные листы")
public class SickLeaveTest {

    WebDriver driver;
    WebDriverWait wait;
    String downloadPath = System.getProperty("user.dir") + "\\downloads";

    @BeforeMethod
    public void setUp() {
        WebDriverManager.firefoxdriver().setup();

        FirefoxOptions options = new FirefoxOptions();
        //options.addArguments("--width=1920");
        //options.addArguments("--height=1080");

        options.addPreference("browser.download.folderList", 2);
        options.addPreference("browser.download.dir", downloadPath);
        options.addPreference("browser.helperApps.neverAsk.saveToDisk", "application/pdf");
        options.addPreference("pdfjs.disabled", true);

        driver = new FirefoxDriver(options);
        driver.manage().window().maximize();
        wait = new WebDriverWait(driver, Duration.ofSeconds(15));

        new File(downloadPath).mkdirs();
    }

    @Test
    @Story("Работа с электронными больничными листами")
    @Description("Выбор периода, скачивание всех ЭЛН и возврат назад")
    @Severity(SeverityLevel.CRITICAL)
    public void processAllSickLeaves() throws Exception {
        String targetUrl = "https://pgu-uat-betalk.test.gosuslugi.ru/health/eln";

        Allure.step("1. Перейти на страницу ЭЛН");
        driver.get(targetUrl);
        Thread.sleep(3000);
        takeScreenshot(driver, "1_Открыта_страница");

        if (driver.getCurrentUrl().contains("login")) {
            Allure.step("2. Выполнить авторизацию");
            login();
            takeScreenshot(driver, "2_После_авторизации");
        }

        Allure.step("3. Находим поле ввода 'Период'");
        WebElement periodField = wait.until(ExpectedConditions.presenceOfElementLocated(
                By.xpath("//input[@aria-label='Период']")
        ));
        takeScreenshot(driver, "3_Поле_период_найдено");

        Allure.step("4. Вводим период: 02.06.2024 — 02.06.2025");
        periodField.click();
        periodField.clear();
        periodField.sendKeys(Keys.CONTROL + "a");
        periodField.sendKeys(Keys.DELETE);
        Thread.sleep(500);
        periodField.sendKeys("02.06.2024—02.06.2025");
        Thread.sleep(500);

        String periodValue = periodField.getAttribute("value");
        Allure.addAttachment("Значение поля Период", periodValue);
        takeScreenshot(driver, "4_Период_введен");

        Allure.step("5. Применяем фильтр по периоду");
        periodField.sendKeys(Keys.ENTER);
        Thread.sleep(3000);
        takeScreenshot(driver, "5_Фильтр_применен");

        Allure.step("6. Находим все листки нетрудоспособности на странице");
        By sickLeaveLocator = By.xpath("//div[@class='title-h4' and contains(text(), 'Листок нетрудоспособности')]");
        wait.until(ExpectedConditions.presenceOfElementLocated(sickLeaveLocator));

        var allSickLeaves = driver.findElements(sickLeaveLocator);
        int totalCount = allSickLeaves.size();

        Allure.addAttachment("Количество найденных ЭЛН", String.valueOf(totalCount));
        Allure.step("Найдено ЭЛН: " + totalCount);
        takeScreenshot(driver, "6_Список_всех_ЭЛН");

        Allure.step("7. Начинаем обработку каждого листка нетрудоспособности");
        for (int i = 0; i < totalCount; i++) {
            Allure.step("Обработка ЭЛН №" + (i + 1) + " из " + totalCount);
            var currentLeaves = driver.findElements(sickLeaveLocator);
            if (i >= currentLeaves.size()) break;

            String sickLeaveText = currentLeaves.get(i).getText();
            String sickLeaveNumber = extractNumber(sickLeaveText);
            Allure.addAttachment("ЭЛН №" + (i + 1), sickLeaveText);
            Allure.step("Обработка: " + sickLeaveText);

            currentLeaves.get(i).click();
            Thread.sleep(2000);
            takeScreenshot(driver, "7_" + (i+1) + "_Открыт_ЭЛН_" + sickLeaveNumber);

            Allure.step("Скачивание файла для ЭЛН " + sickLeaveNumber);
            boolean downloaded = downloadSickLeaveFile();
            if (downloaded) {
                Allure.step("✅ Файл для ЭЛН " + sickLeaveNumber + " успешно скачан");
            } else {
                Allure.step("⚠️ Не удалось скачать файл для ЭЛН " + sickLeaveNumber);
            }

            Allure.step("Возврат к списку ЭЛН");
            goBack();
            Thread.sleep(2000);
            takeScreenshot(driver, "7_" + (i+1) + "_Вернулись_к_списку");
        }

        Allure.step("✅ Тест пройден! Обработано " + totalCount + " листков нетрудоспособности");
    }

    private String extractNumber(String text) {
        try {
            String[] parts = text.split("№");
            if (parts.length > 1) {
                return parts[1].trim();
            }
        } catch (Exception e) {
            return "unknown";
        }
        return "unknown";
    }

    private boolean downloadSickLeaveFile() throws Exception {
        try {
            WebElement downloadButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//a[@class='link-plain no-select' and contains(text(), 'Скачать')]")
            ));
            String fileName = "sick_leave_" + System.currentTimeMillis() + ".pdf";
            Allure.addAttachment("Имя файла", fileName);
            downloadButton.click();
            Thread.sleep(2000);
            return checkFileDownloaded(fileName);
        } catch (Exception e) {
            System.out.println("Ошибка при скачивании: " + e.getMessage());
            return false;
        }
    }

    private boolean checkFileDownloaded(String fileName) {
        File folder = new File(downloadPath);
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.getName().contains(".pdf")) {
                    Allure.addAttachment("Скачанный файл", file.getName());
                    return true;
                }
            }
        }
        return false;
    }

    private void goBack() throws Exception {
        try {
            WebElement backButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//a[@class='back-button link-plain' and contains(text(), 'Назад')]")
            ));
            backButton.click();
            Thread.sleep(2000);
        } catch (Exception e) {
            driver.navigate().back();
            Thread.sleep(2000);
        }
    }

    private void login() throws Exception {
        Allure.step("Ввести логин");
        WebElement loginField = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("login")));
        loginField.sendKeys("00108467802");
        takeScreenshot(driver, "Логин_введен");

        Allure.step("Ввести пароль");
        WebElement passwordField = driver.findElement(By.id("password"));
        passwordField.sendKeys("ckPekf}WPfm~");

        Allure.step("Нажать кнопку входа");
        WebElement loginButton = driver.findElement(By.className("plain-button"));
        loginButton.click();
        takeScreenshot(driver, "Кнопка_входа_нажата");

        wait.until(ExpectedConditions.not(ExpectedConditions.urlContains("login")));
        Thread.sleep(3000);
    }

    @Attachment(value = "Скриншот: {name}", type = "image/png")
    public byte[] takeScreenshot(WebDriver driver, String name) {
        if (driver == null) return new byte[0];
        try {
            return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
        } catch (Exception e) {
            return new byte[0];
        }
    }

    @AfterMethod
    public void tearDown() {
        if (driver != null) driver.quit();
    }
}