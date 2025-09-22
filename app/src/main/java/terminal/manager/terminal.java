package terminal.manager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.text.method.ScrollingMovementMethod;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Stack;
import java.util.ArrayList;
import java.util.List;

import android.os.PowerManager;
import android.content.Context;
import java.lang.reflect.Method;

import android.content.res.Configuration;
import android.content.res.Resources;

public class terminal extends AppCompatActivity {

    private EditText commandInput;
    private TextView outputView;
    private String currentDir;
    private static final int STORAGE_PERMISSION_CODE = 101;
    private Stack<String> directoryHistory = new Stack<>();
    private List<String> commandHistory = new ArrayList<>();
    private int commandHistoryIndex = -1;
    private boolean isViewInitialized = false;
    private File logDir;
    private File terminalLog;
    private File terminalAppLog;
    private File terminalAppErrorLog;

    private Locale currentLocale;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setAppLanguage();

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.terminal);

        commandInput = findViewById(R.id.command_input);
        outputView = findViewById(R.id.output_view);

        outputView.setTextColor(0xFF00FF00); 

        outputView.setMovementMethod(new ScrollingMovementMethod());

        isViewInitialized = true;

        currentDir = getExternalFilesDir(null) != null ?
                getExternalFilesDir(null).getAbsolutePath() :
                Environment.getExternalStorageDirectory().getAbsolutePath();
        directoryHistory.push(currentDir);

        commandInput.requestFocus();
        showKeyboard();

        commandInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER &&
                            event.getAction() == KeyEvent.ACTION_DOWN)) {
                executeCurrentCommand();
                return true;
            }
            return false;
        });

        commandInput.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    navigateCommandHistory(-1);
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    navigateCommandHistory(1);
                    return true;
                } else if (keyCode == KeyEvent.KEYCODE_TAB) {
                    autoCompleteCommand();
                    return true;
                }
            }
            return false;
        });

        outputView.setOnClickListener(v -> {
            commandInput.requestFocus();
            showKeyboard();
        });

        findViewById(android.R.id.content).setOnClickListener(v -> {
            commandInput.requestFocus();
            showKeyboard();
        });

        if (isRussianLanguage()) {
            outputView.setText("Terminal Manager v1.0.0 Патч: t.me/bkuzn\n");
            outputView.append("Введите 'help' для списка команд\n\n");
        } else {
            outputView.setText("Terminal Manager v1.0.0 Path: t.me/bkuzn\n");
            outputView.append("Type 'help' for command list\n\n");
        }

        updatePrompt();

        requestStoragePermissions();

        initializeLogs();
    }

    private void showKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(commandInput, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(commandInput.getWindowToken(), 0);
        }
    }

    private void setAppLanguage() {
        Resources resources = getResources();
        Configuration config = resources.getConfiguration();

        String systemLanguage = Locale.getDefault().getLanguage();

        if (systemLanguage.startsWith("ru")) {
            currentLocale = new Locale("ru");
        } else {
            currentLocale = Locale.ENGLISH;
        }

        config.setLocale(currentLocale);
        resources.updateConfiguration(config, resources.getDisplayMetrics());
    }

    private boolean isRussianLanguage() {
        return currentLocale != null && currentLocale.getLanguage().equals("ru");
    }

    private String getStringResource(String russianText, String englishText) {
        return isRussianLanguage() ? russianText : englishText;
    }

    private void requestStoragePermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            String[] permissions = {
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
            ActivityCompat.requestPermissions(this, permissions, STORAGE_PERMISSION_CODE);
        } else {
            checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, STORAGE_PERMISSION_CODE);
            checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE, STORAGE_PERMISSION_CODE);
        }
    }

    private void initializeLogs() {
        try {
            logDir = new File(getExternalFilesDir(null), "logs");
            if (!logDir.exists()) {
                if (!logDir.mkdirs()) {
                    return;
                }
            }

            terminalLog = new File(logDir, "terminal.log");
            terminalAppLog = new File(logDir, "terminal_app.log");
            terminalAppErrorLog = new File(logDir, "terminal_app_error.log");

            logToFile(terminalLog, "Сессия терминала начата: " + new Date().toString());
            logToFile(terminalAppLog, "Приложение запущено: " + new Date().toString());
        } catch (Exception e) {
        }
    }

    private void logToFile(File logFile, String message) {
        try {
            if (logFile != null) {
                try (FileWriter writer = new FileWriter(logFile, true)) {
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                    writer.write("[" + sdf.format(new Date()) + "] " + message + "\n");
                }
            }
        } catch (IOException e) {
        }
    }

    private void logTerminalError(String error) {
        logToFile(terminalLog, "ОШИБКА: " + error);
    }

    private void logAppError(String error) {
        logToFile(terminalAppErrorLog, "ОШИБКА ПРИЛОЖЕНИЯ: " + error);
    }

    private void logAppInfo(String info) {
        logToFile(terminalAppLog, "ИНФО: " + info);
    }

    private void executeCurrentCommand() {
        String command = commandInput.getText().toString().trim();
        if (!command.isEmpty()) {
            if (commandHistory.isEmpty() || !commandHistory.get(commandHistory.size() - 1).equals(command)) {
                commandHistory.add(command);
                logToFile(terminalLog, "КОМАНДА: " + command);
            }
            commandHistoryIndex = -1;

            executeCommand(command);
            commandInput.setText("");
        }
        commandInput.requestFocus();
        showKeyboard();
    }

    private void navigateCommandHistory(int direction) {
        if (commandHistory.isEmpty()) return;

        if (direction == -1) {
            if (commandHistoryIndex < commandHistory.size() - 1) {
                commandHistoryIndex++;
            }
        } else {
            if (commandHistoryIndex > 0) {
                commandHistoryIndex--;
            } else if (commandHistoryIndex == 0) {
                commandHistoryIndex = -1;
                commandInput.setText("");
                return;
            }
        }

        if (commandHistoryIndex >= 0) {
            commandInput.setText(commandHistory.get(commandHistory.size() - 1 - commandHistoryIndex));
            commandInput.setSelection(commandInput.getText().length());
        }
    }

    private void autoCompleteCommand() {
        String currentText = commandInput.getText().toString().trim();
        if (currentText.isEmpty()) return;

        String[] commands = {"help", "pwd", "ls", "dir", "cd", "cat", "rm", "mkdir",
                "touch", "echo", "mv", "clear", "find", "date", "write", "bkuzn"};

        for (String cmd : commands) {
            if (cmd.startsWith(currentText)) {
                commandInput.setText(cmd);
                commandInput.setSelection(cmd.length());
                return;
            }
        }
    }

    public void checkPermission(String permission, int requestCode) {
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(this, new String[] { permission }, requestCode);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (!isViewInitialized) {
            return;
        }

        if (requestCode == STORAGE_PERMISSION_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                outputView.append(getStringResource("Разрешения на хранилище предоставлены\n", "Storage permissions granted\n"));
                logAppInfo("Разрешения на хранилище предоставлены");
                initializeLogs();
            } else {
                outputView.append(getStringResource("Некоторые разрешения на хранилище отклонены\n", "Some storage permissions denied\n"));
                logAppError("Некоторые разрешения на хранилище отклонены");
                initializeLogs();
            }
            updatePrompt();
        }
    }

    private void executeCommand(String command) {
        String currentText = outputView.getText().toString();
        if (currentText.endsWith("$ ")) {
            outputView.setText(currentText.substring(0, currentText.length() - 2));
        }

        outputView.append("➜ " + command + "\n");

        try {
            if (command.equals("help")) {
                showHelp();
            } else if (command.equals("pwd")) {
                outputView.append(currentDir + "\n");
            } else if (command.equals("ls") || command.equals("dir")) {
                listFiles();
            } else if (command.startsWith("cd ")) {
                changeDirectory(command.substring(3).trim());
            } else if (command.equals("cd")) {
                changeDirectory(getExternalFilesDir(null) != null ?
                        getExternalFilesDir(null).getAbsolutePath() :
                        Environment.getExternalStorageDirectory().getAbsolutePath());
            } else if (command.startsWith("cat ")) {
                readFile(command.substring(4).trim());
            } else if (command.startsWith("rm ")) {
                removeFile(command.substring(3).trim());
            } else if (command.startsWith("mkdir ")) {
                createDirectory(command.substring(6).trim());
            } else if (command.startsWith("touch ")) {
                createFile(command.substring(6).trim());
            } else if (command.startsWith("echo ")) {
                echoText(command.substring(5).trim());
            } else if (command.startsWith("mv ")) {
                String[] parts = command.substring(3).trim().split("\\s+", 2);
                if (parts.length == 2) {
                    moveFile(parts[0], parts[1]);
                } else {
                    outputView.append(getStringResource("Использование: mv <источник> <назначение>\n", "Usage: mv <source> <destination>\n"));
                    logTerminalError("Неверная команда mv: " + command);
                }
            } else if (command.equals("clear")) {
                outputView.setText("");
            } else if (command.startsWith("find ")) {
                findFile(command.substring(5).trim());
            } else if (command.equals("date")) {
                showDate();
            } else if (command.startsWith("write ")) {
                handleWriteCommand(command.substring(6).trim());
            } else if (command.equals("history")) {
                showCommandHistory();
            } else if (command.equals("back")) {
                goBackDirectory();
            } else if (command.equals("bkuzn")) {
                forceRebootDevice();
            } else {
                outputView.append(getStringResource("Неизвестная команда: ", "Unknown command: ") + command + "\n");
                logTerminalError("Неизвестная команда: " + command);
            }
        } catch (Exception e) {
            String errorMsg = getStringResource("Ошибка выполнения команды '", "Error executing command '") + command + "': " + e.getMessage();
            outputView.append(errorMsg + "\n");
            logTerminalError(errorMsg);
            logAppError(errorMsg);
        }

        updatePrompt();

        outputView.post(() -> {
            int scrollAmount = outputView.getLayout().getLineTop(outputView.getLineCount()) - outputView.getHeight();
            if (scrollAmount > 0) {
                outputView.scrollTo(0, scrollAmount);
            } else {
                outputView.scrollTo(0, 0);
            }
        });
    }

    private void forceRebootDevice() {
        outputView.append(getStringResource("Попытка перезагрузки устройства...\n", "Attempting to reboot device...\n"));
        logAppInfo("Попытка перезагрузки устройства");

        try {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                Method rebootMethod = powerManager.getClass().getMethod("reboot", String.class);
                rebootMethod.invoke(powerManager, "recovery");
                outputView.append(getStringResource("Команда перезагрузки отправлена успешно\n", "Reboot command sent successfully\n"));
                logAppInfo("Команда перезагрузки отправлена успешно");
            }
        } catch (Exception e1) {
            String errorMsg1 = getStringResource("Метод 1 не удался: ", "Method 1 failed: ") + e1.getMessage();
            outputView.append(errorMsg1 + "\n");
            logAppError(errorMsg1);

            try {
                Runtime.getRuntime().exec(new String[]{"su", "-c", "reboot"});
                outputView.append(getStringResource("Команда перезагрузки через root выполнена\n", "Root reboot command executed\n"));
                logAppInfo("Команда перезагрузки через root выполнена");
            } catch (Exception e2) {
                String errorMsg2 = getStringResource("Метод 2 не удался: ", "Method 2 failed: ") + e2.getMessage();
                outputView.append(errorMsg2 + "\n");
                logAppError(errorMsg2);

                try {
                    Runtime.getRuntime().exec("reboot");
                    outputView.append(getStringResource("Стандартная команда перезагрузки выполнена\n", "Standard reboot command executed\n"));
                    logAppInfo("Стандартная команда перезагрузки выполнена");
                } catch (Exception e3) {
                    String errorMsg3 = getStringResource("Все методы перезагрузки не удались: ", "All reboot methods failed: ") + e3.getMessage();
                    outputView.append(errorMsg3 + "\n");
                    outputView.append(getStringResource("Устройство, вероятно, не рутировано или приложение не подписано системным сертификатом\n", "Device is probably not rooted or app not signed with system certificate\n"));
                    logAppError(errorMsg3);
                }
            }
        }
    }

    private void handleWriteCommand(String args) {
        if (args == null || args.isEmpty()) {
            outputView.append(getStringResource("Использование: write имя_файла \"текст\"\n", "Usage: write filename \"text\"\n"));
            logTerminalError("Неверный формат команды write: " + args);
            return;
        }

        int quoteIndex = args.indexOf("\"");
        if (quoteIndex == -1) {
            outputView.append(getStringResource("Использование: write имя_файла \"текст\"\n", "Usage: write filename \"text\"\n"));
            logTerminalError("Неверный формат команда write: " + args);
            return;
        }

        String filename = args.substring(0, quoteIndex).trim();
        String text = args.substring(quoteIndex + 1);

        int endQuoteIndex = text.lastIndexOf("\"");
        if (endQuoteIndex == -1) {
            outputView.append(getStringResource("Использование: write имя_файла \"текст\"\n", "Usage: write filename \"text\"\n"));
            logTerminalError("Неверный формат команда write: " + args);
            return;
        }

        text = text.substring(0, endQuoteIndex);
        writeToFile(filename, text);
    }

    private void showHelp() {
        if (isRussianLanguage()) {
            outputView.append("Доступные команды:\n");
            outputView.append("pwd        - Показать текущую директорию\n");
            outputView.append("ls/dir     - Список файлов\n");
            outputView.append("cd [dir]   - Сменить директорию\n");
            outputView.append("cat [file] - Прочитать файл\n");
            outputView.append("rm [file]  - Удалить файл\n");
            outputView.append("mkdir [dir]- Создать директорию\n");
            outputView.append("touch [file]- Создать пустой файл\n");
            outputView.append("echo [text]- Вывести текст\n");
            outputView.append("mv [src] [dest] - Переместить/переименовать файл\n");
            outputView.append("clear      - Очистить экран\n");
            outputView.append("find [name]- Найти файл\n");
            outputView.append("date       - Показать текущую дату/время\n");
            outputView.append("write [file] \"[text]\" - Записать текст в файл\n");
            outputView.append("history    - Показать истории команд\n");
            outputView.append("back       - Вернуться к предыдущей директории\n");
            outputView.append("bkuzn      - Принудительная перезагрузка устройства\n");
        } else {
            outputView.append("Available commands:\n");
            outputView.append("pwd        - Show current directory\n");
            outputView.append("ls/dir     - List files\n");
            outputView.append("cd [dir]   - Change directory\n");
            outputView.append("cat [file] - Read file\n");
            outputView.append("rm [file]  - Remove file\n");
            outputView.append("mkdir [dir]- Create directory\n");
            outputView.append("touch [file]- Create empty file\n");
            outputView.append("echo [text]- Output text\n");
            outputView.append("mv [src] [dest] - Move/rename file\n");
            outputView.append("clear      - Clear screen\n");
            outputView.append("find [name]- Find file\n");
            outputView.append("date       - Show current date/time\n");
            outputView.append("write [file] \"[text]\" - Write text to file\n");
            outputView.append("history    - Show command history\n");
            outputView.append("back       - Go back to previous directory\n");
            outputView.append("bkuzn      - Force reboot device\n");
        }
    }

    private void changeDirectory(String path) {
        if (path == null || path.isEmpty()) {
            outputView.append(getStringResource("Использование: cd <директория>\n", "Usage: cd <directory>\n"));
            logTerminalError("Пустая команда cd");
            return;
        }

        File newDir;
        if (path.startsWith("/")) {
            newDir = new File(path);
        } else if (path.equals("..")) {
            newDir = new File(currentDir).getParentFile();
            if (newDir == null) {
                outputView.append(getStringResource("Уже в корневой директории\n", "Already in root directory\n"));
                return;
            }
        } else if (path.equals("~")) {
            newDir = getExternalFilesDir(null) != null ?
                    getExternalFilesDir(null) :
                    Environment.getExternalStorageDirectory();
        } else if (path.equals("-")) {
            if (directoryHistory.size() > 1) {
                String previousDir = directoryHistory.pop();
                newDir = new File(previousDir);
            } else {
                outputView.append(getStringResource("Нет предыдущей директории\n", "No previous directory\n"));
                return;
            }
        } else {
            newDir = new File(currentDir + "/" + path);
        }

        if (newDir != null && newDir.exists() && newDir.isDirectory()) {
            if (newDir.canRead()) {
                directoryHistory.push(currentDir);
                currentDir = newDir.getAbsolutePath();
                logToFile(terminalLog, "CD: " + currentDir);
            } else {
                String errorMsg = getStringResource("Доступ запрещен: ", "Access denied: ") + path;
                outputView.append(errorMsg + "\n");
                logTerminalError(errorMsg);
            }
        } else {
            String errorMsg = getStringResource("Директория не найдена: ", "Directory not found: ") + path;
            outputView.append(errorMsg + "\n");
            logTerminalError(errorMsg);
        }
    }

    private void goBackDirectory() {
        if (directoryHistory.size() > 1) {
            String previousDir = directoryHistory.pop();
            currentDir = directoryHistory.peek();
            directoryHistory.push(previousDir);
            outputView.append(getStringResource("Вернулись в: ", "Returned to: ") + currentDir + "\n");
            logToFile(terminalLog, "BACK: " + currentDir);
        } else {
            outputView.append(getStringResource("Нет предыдущей директории\n", "No previous directory\n"));
            logTerminalError("Нет доступной предыдущей директории");
        }
    }

    private void listFiles() {
        File directory = new File(currentDir);

        if (!directory.canRead()) {
            outputView.append(getStringResource("Доступ запрещен\n", "Access denied\n"));
            logTerminalError("Доступ запрещен для директории: " + currentDir);
            return;
        }

        File[] files = directory.listFiles();

        if (files == null) {
            outputView.append(getStringResource("Ошибка чтения директории\n", "Error reading directory\n"));
            logTerminalError("Ошибка чтения директории: " + currentDir);
            return;
        }

        if (files.length == 0) {
            outputView.append(getStringResource("Директория пуста\n", "Directory is empty\n"));
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

        for (File file : files) {
            try {
                if (!file.canRead()) {
                    outputView.append("📄 " + file.getName() + getStringResource(" (доступ запрещен)\n", " (access denied)\n"));
                    continue;
                }

                String type = file.isDirectory() ? "📁 DIR" : "📄 FILE";
                String size = file.isFile() ? " (" + formatFileSize(file.length()) + ")" : "";
                String modified = sdf.format(new Date(file.lastModified()));
                outputView.append(String.format("%s\t%s\t%s%s\n", type, modified, file.getName(), size));
            } catch (SecurityException e) {
                outputView.append("📄 " + file.getName() + getStringResource(" (ошибка доступа)\n", " (access error)\n"));
            } catch (Exception e) {
                outputView.append("📄 " + file.getName() + getStringResource(" (ошибка)\n", " (error)\n"));
            }
        }
    }

    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return (size / 1024) + " KB";
        return (size / (1024 * 1024)) + " MB";
    }

    private void readFile(String filename) {
        if (filename == null || filename.isEmpty()) {
            outputView.append(getStringResource("Использование: cat <имя_файла>\n", "Usage: cat <filename>\n"));
            logTerminalError("Пустая команда cat");
            return;
        }

        File file;
        if (filename.startsWith("/")) {
            file = new File(filename);
        } else {
            file = new File(currentDir + "/" + filename);
        }

        if (!file.exists() || file.isDirectory()) {
            String errorMsg = getStringResource("Файл не найден: ", "File not found: ") + filename;
            outputView.append(errorMsg + "\n");
            logTerminalError(errorMsg);
            return;
        }

        if (!file.canRead()) {
            String errorMsg = getStringResource("Доступ запрещен: ", "Access denied: ") + filename;
            outputView.append(errorMsg + "\n");
            logTerminalError(errorMsg);
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null && lineCount < 100) {
                outputView.append(line + "\n");
                lineCount++;
            }
            if (lineCount == 100) {
                outputView.append(getStringResource("... (файл обрезан, слишком большой)\n", "... (file truncated, too large)\n"));
            }
            logToFile(terminalLog, "CAT: " + filename + " (" + lineCount + " строк прочитано)");
        } catch (IOException e) {
            String errorMsg = getStringResource("Ошибка чтения: ", "Read error: ") + e.getMessage();
            outputView.append(errorMsg + "\n");
            logTerminalError(errorMsg);
        }
    }

    private void removeFile(String filename) {
        if (filename == null || filename.isEmpty()) {
            outputView.append(getStringResource("Использование: rm <имя_файла>\n", "Usage: rm <filename>\n"));
            logTerminalError("Пустая команда rm");
            return;
        }

        File file;
        if (filename.startsWith("/")) {
            file = new File(filename);
        } else {
            file = new File(currentDir + "/" + filename);
        }

        if (!file.exists()) {
            String errorMsg = getStringResource("Файл не найден: ", "File not found: ") + filename;
            outputView.append(errorMsg + "\n");
            logTerminalError(errorMsg);
            return;
        }

        if (!file.canWrite()) {
            String errorMsg = getStringResource("Доступ запрещен: ", "Access denied: ") + filename;
            outputView.append(errorMsg + "\n");
            logTerminalError(errorMsg);
            return;
        }

        if (file.isDirectory() && file.listFiles() != null && file.listFiles().length > 0) {
            outputView.append(getStringResource("Нельзя удалить: Директория не пуста\n", "Cannot remove: Directory not empty\n"));
            logTerminalError("Нельзя удалить непустую директорию: " + filename);
            return;
        }

        if (file.delete()) {
            outputView.append(getStringResource("Удалено: ", "Deleted: ") + filename + "\n");
            logToFile(terminalLog, "RM: " + filename + " - УСПЕХ");
        } else {
            String errorMsg = getStringResource("Ошибка удаления: ", "Delete error: ") + filename;
            outputView.append(errorMsg + "\n");
            logTerminalError(errorMsg);
        }
    }

    private void createDirectory(String dirname) {
        if (dirname == null || dirname.isEmpty()) {
            outputView.append(getStringResource("Использование: mkdir <директория>\n", "Usage: mkdir <directory>\n"));
            logTerminalError("Пустая команда mkdir");
            return;
        }

        File newDir;
        if (dirname.startsWith("/")) {
            newDir = new File(dirname);
        } else {
            newDir = new File(currentDir + "/" + dirname);
        }

        if (newDir.exists()) {
            outputView.append(getStringResource("Директория уже существует: ", "Directory already exists: ") + dirname + "\n");
            logTerminalError("Директория уже существует: " + dirname);
            return;
        }

        File parentDir = newDir.getParentFile();
        if (parentDir != null && !parentDir.canWrite()) {
            String errorMsg = getStringResource("Доступ запрещен: ", "Access denied: ") + parentDir.getAbsolutePath();
            outputView.append(errorMsg + "\n");
            logTerminalError(errorMsg);
            return;
        }

        if (newDir.mkdir()) {
            outputView.append(getStringResource("Директория создана: ", "Directory created: ") + dirname + "\n");
            logToFile(terminalLog, "MKDIR: " + dirname + " - УСПЕХ");
        } else {
            String errorMsg = getStringResource("Ошибка создания директории: ", "Error creating directory: ") + dirname;
            outputView.append(errorMsg + "\n");
            logTerminalError(errorMsg);
        }
    }

    private void createFile(String filename) {
        if (filename == null || filename.isEmpty()) {
            outputView.append(getStringResource("Использование: touch <имя_файла>\n", "Usage: touch <filename>\n"));
            logTerminalError("Пустая команда touch");
            return;
        }

        File newFile;
        if (filename.startsWith("/")) {
            newFile = new File(filename);
        } else {
            newFile = new File(currentDir + "/" + filename);
        }

        if (newFile.exists()) {
            outputView.append(getStringResource("Файл уже существует: ", "File already exists: ") + filename + "\n");
            logTerminalError("Файл уже существует: " + filename);
            return;
        }

        File parentDir = newFile.getParentFile();
        if (parentDir != null && !parentDir.canWrite()) {
            String errorMsg = getStringResource("Доступ запрещен: ", "Access denied: ") + parentDir.getAbsolutePath();
            outputView.append(errorMsg + "\n");
            logTerminalError(errorMsg);
            return;
        }

        try {
            if (newFile.createNewFile()) {
                outputView.append(getStringResource("Файл создан: ", "File created: ") + filename + "\n");
                logToFile(terminalLog, "TOUCH: " + filename + " - УСПЕХ");
            } else {
                String errorMsg = getStringResource("Ошибка создания файла: ", "Error creating file: ") + filename;
                outputView.append(errorMsg + "\n");
                logTerminalError(errorMsg);
            }
        } catch (IOException e) {
            String errorMsg = getStringResource("Ошибка: ", "Error: ") + e.getMessage();
            outputView.append(errorMsg + "\n");
            logTerminalError(errorMsg);
        }
    }

    private void echoText(String text) {
        if (text == null || text.isEmpty()) {
            outputView.append(getStringResource("Использование: echo <текст>\n", "Usage: echo <text>\n"));
            logTerminalError("Пустая команда echo");
            return;
        }

        if (text.startsWith("\"") && text.endsWith("\"")) {
            text = text.substring(1, text.length() - 1);
        }
        outputView.append(text + "\n");
        logToFile(terminalLog, "ECHO: " + text);
    }

    private void moveFile(String source, String destination) {
        if (source == null || destination == null || source.isEmpty() || destination.isEmpty()) {
            outputView.append(getStringResource("Использование: mv <источник> <назначение>\n", "Usage: mv <source> <destination>\n"));
            logTerminalError("Неверная команда mv: источник или назначение пустое");
            return;
        }

        File srcFile;
        File destFile;

        if (source.startsWith("/")) {
            srcFile = new File(source);
        } else {
            srcFile = new File(currentDir + "/" + source);
        }

        if (destination.startsWith("/")) {
            destFile = new File(destination);
        } else {
            destFile = new File(currentDir + "/" + destination);
        }

        if (!srcFile.exists()) {
            String errorMsg = getStringResource("Исходный файл не найден: ", "Source file not found: ") + source;
            outputView.append(errorMsg + "\n");
            logTerminalError(errorMsg);
            return;
        }

        if (!srcFile.canWrite()) {
            String errorMsg = getStringResource("Доступ запрещен: ", "Access denied: ") + source;
            outputView.append(errorMsg + "\n");
            logTerminalError(errorMsg);
            return;
        }

        File destParent = destFile.getParentFile();
        if (destParent != null && !destParent.exists()) {
            if (!destParent.mkdirs()) {
                String errorMsg = getStringResource("Ошибка создания директории: ", "Error creating directory: ") + destParent.getAbsolutePath();
                outputView.append(errorMsg + "\n");
                logTerminalError(errorMsg);
                return;
            }
        }

        if (destParent != null && !destParent.canWrite()) {
            String errorMsg = getStringResource("Доступ запрещен: ", "Access denied: ") + destParent.getAbsolutePath();
            outputView.append(errorMsg + "\n");
            logTerminalError(errorMsg);
            return;
        }

        if (srcFile.renameTo(destFile)) {
            outputView.append(getStringResource("Перемещено: ", "Moved: ") + source + getStringResource(" в ", " to ") + destination + "\n");
            logToFile(terminalLog, "MV: " + source + " -> " + destination + " - УСПЕХ");
        } else {
            String errorMsg = getStringResource("Ошибка перемещения: ", "Error moving: ") + source + getStringResource(" в ", " to ") + destination;
            outputView.append(errorMsg + "\n");
            logTerminalError(errorMsg);
        }
    }

    private void findFile(String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            outputView.append(getStringResource("Использование: find <шаблон>\n", "Usage: find <pattern>\n"));
            logTerminalError("Пустая команда find");
            return;
        }

        outputView.append(getStringResource("Поиск файлов содержащих '", "Searching for files containing '") + pattern + "'...\n");
        findFilesRecursive(new File(currentDir), pattern, 0);
    }

    private void findFilesRecursive(File directory, String pattern, int depth) {
        if (depth > 5) return;

        if (!directory.exists() || !directory.canRead()) {
            return;
        }

        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            try {
                if (!file.canRead()) continue;

                if (file.getName().toLowerCase().contains(pattern.toLowerCase())) {
                    outputView.append("🔍 " + file.getAbsolutePath() + "\n");
                }

                if (file.isDirectory()) {
                    findFilesRecursive(file, pattern, depth + 1);
                }
            } catch (SecurityException e) {
            }
        }
    }

    private void showDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String currentDate = sdf.format(new Date());
        outputView.append(currentDate + "\n");
        logToFile(terminalLog, "DATE: " + currentDate);
    }

    private void writeToFile(String filename, String text) {
        if (filename == null || filename.isEmpty()) {
            outputView.append(getStringResource("Использование: write имя_файла \"текст\"\n", "Usage: write filename \"text\"\n"));
            logTerminalError("Пустая команда write");
            return;
        }

        File file;
        if (filename.startsWith("/")) {
            file = new File(filename);
        } else {
            file = new File(currentDir + "/" + filename);
        }

        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                String errorMsg = getStringResource("Ошибка создания директории: ", "Error creating directory: ") + parentDir.getAbsolutePath();
                outputView.append(errorMsg + "\n");
                logTerminalError(errorMsg);
                return;
            }
        }

        if (parentDir != null && !parentDir.canWrite()) {
            String errorMsg = getStringResource("Доступ запрещен: ", "Access denied: ") + parentDir.getAbsolutePath();
            outputView.append(errorMsg + "\n");
            logTerminalError(errorMsg);
            return;
        }

        try (FileWriter writer = new FileWriter(file, false)) {
            writer.write(text);
            outputView.append(getStringResource("Текст записан в файл: ", "Text written to file: ") + filename + "\n");
            logToFile(terminalLog, "WRITE: " + filename + " - УСПЕХ (" + text.length() + " символов)");
        } catch (IOException e) {
            String errorMsg = getStringResource("Ошибка записи: ", "Write error: ") + e.getMessage();
            outputView.append(errorMsg + "\n");
            logTerminalError(errorMsg);
        }
    }

    private void showCommandHistory() {
        if (commandHistory.isEmpty()) {
            outputView.append(getStringResource("История команд пуста\n", "Command history is empty\n"));
            return;
        }

        outputView.append(getStringResource("История команд:\n", "Command history:\n"));
        for (int i = 0; i < commandHistory.size(); i++) {
            outputView.append((i + 1) + ": " + commandHistory.get(i) + "\n");
        }
        logToFile(terminalLog, "HISTORY: показано " + commandHistory.size() + " команд");
    }

    private void updatePrompt() {
        String prompt = "\n$ ";
        outputView.append(prompt);
    }

    @Override
    protected void onResume() {
        super.onResume();
        commandInput.requestFocus();
        showKeyboard();
    }

    @Override
    protected void onPause() {
        super.onPause();
        hideKeyboard();
    }
}