package org.example;

import org.example.model.Post;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class MyTelegramBot extends TelegramLongPollingBot {

    @Value("${telegram.bot.username}")
    private String botUsername;

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.moderatorChatIds}")
    private List<Long> moderatorChatIds;

    private Map<Long, Post> postMap = new HashMap<>();
    private Map<Long, State> userStateMap = new HashMap<>();

    enum State {
        START,
        SELECT_THEME,
        ENTER_TITLE,
        ENTER_TEXT,
        ADD_IMAGE,
        DOWNLOAD_IMAGE,
        ENTER_AUTHOR,
        AWAIT_MODERATION
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText() || update.getMessage().hasPhoto()) {
            long chatId = update.getMessage().getChatId();
            String messageText = update.getMessage().getText();

            State state = userStateMap.getOrDefault(chatId, State.START);
            switch (state) {
                case START:
                    handleStart(chatId);
                    break;
                case SELECT_THEME:
                    handleSelectTheme(chatId, messageText);
                    break;
                case ENTER_TITLE:
                    handleEnterTitle(chatId, messageText);
                    break;
                case ENTER_TEXT:
                    handleEnterText(chatId, messageText);
                    break;
                case ADD_IMAGE:
                    handleAddImage(chatId, messageText);
                    break;
                case DOWNLOAD_IMAGE:
                    handleDownloadImage(chatId, update.getMessage());
                    break;
                case ENTER_AUTHOR:
                    handleEnterAuthor(chatId, messageText);
                    break;
                case AWAIT_MODERATION:
                    if ("в начало".equals(messageText)) {
                        // Возвращаемся к главному меню с кнопкой "Написать пост"
                        sendMessage(chatId, "Вы вернулись к написанию поста.");
                        userStateMap.put(chatId, State.START);
                        handleStart(chatId);  // Возвращаем пользователя к главному меню
                        cleanPost(chatId);
                    } else {
                        sendMessage(chatId, "Неизвестная команда. Попробуйте снова.");
                    }
                    break;
                default:
                    sendMessage(chatId, "Неизвестное состояние. Попробуйте снова.");
                    userStateMap.put(chatId, State.START);
                    handleStart(chatId);
            }
        }
    }

    private void handleStart(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Добро пожаловать! Выберите действие:");
        message.setReplyMarkup(getMainMenuKeyboard());
        executeMessage(message);
        userStateMap.put(chatId, State.SELECT_THEME);
    }

    private void handleSelectTheme(long chatId, String messageText) {
        if (messageText.equals("Написать пост")) {
            sendMessage(chatId, "Выберите тематику:", getThemesKeyboard());
            userStateMap.put(chatId, State.SELECT_THEME);
        } else if (messageText.equals("F.A.Q")) {
            sendMessage(chatId, "Правила: ...");
            userStateMap.put(chatId, State.START);
        } else if (messageText.matches("HR|Маркетинг|IT|Другое")) {
            postMap.put(chatId, new Post(messageText));
            sendMessage(chatId, "Введите заголовок:", getReturnToPostCreationKeyboard());
            userStateMap.put(chatId, State.ENTER_TITLE);
        } else if (messageText.equals("в начало")) {
            userStateMap.put(chatId, State.SELECT_THEME);
            sendMessage(chatId, "Выберите действие:", getMainMenuKeyboard());
            cleanPost(chatId);
        } else {
            sendMessage(chatId, "Пожалуйста, выберите тематику.");
        }
    }

    private void handleEnterTitle(long chatId, String messageText) {
        if (messageText.equals("в начало")) {
            userStateMap.put(chatId, State.SELECT_THEME);
            sendMessage(chatId, "Выберите действие:", getMainMenuKeyboard());
            cleanPost(chatId);
        } else {
            Post post = postMap.get(chatId);
            post.setTitle(messageText);
            sendMessage(chatId, "Напишите текст публикации:", getReturnToPostCreationKeyboard());
            userStateMap.put(chatId, State.ENTER_TEXT);
        }
    }

    private void handleEnterText(long chatId, String messageText) {
        if (messageText.equals("в начало")) {
            userStateMap.put(chatId, State.SELECT_THEME);
            sendMessage(chatId, "Выберите действие:", getMainMenuKeyboard());
            cleanPost(chatId);
        } else {
            Post post = postMap.get(chatId);
            post.setText(messageText);
            sendMessage(chatId, "Добавить изображение?", getYesNoKeyboard());
            userStateMap.put(chatId, State.ADD_IMAGE);
        }
    }

    private void handleAddImage(long chatId, String messageText) {
        if (messageText.equals("Да")) {
            sendMessage(chatId, "Пожалуйста, загрузите изображение.", getEndAddImage());
            userStateMap.put(chatId, State.DOWNLOAD_IMAGE); // Ждем загрузки изображения
        } else if (messageText.equals("в начало")) {
            userStateMap.put(chatId, State.SELECT_THEME);
            sendMessage(chatId, "Выберите действие:", getMainMenuKeyboard());
            cleanPost(chatId);
        } else {
            sendMessage(chatId, "Укажите авторство, написав Ваше имя и фамилию, а также ваш ник в Telegram через символ @", getReturnToPostCreationKeyboard());
            userStateMap.put(chatId, State.ENTER_AUTHOR);
        }
    }

    private void handleDownloadImage(long chatId, Message message) {
        if (message != null && message.hasPhoto()) {
            // Получаем самое большое изображение
            PhotoSize photo = message.getPhoto().get(message.getPhoto().size() - 1);
            try {
                String fileId = photo.getFileId();
                String filePath = execute(new GetFile(fileId)).getFilePath();

                // Скачиваем файл и сохраняем его
                File file = new File("images/" + fileId + ".jpg");
                downloadFile(filePath, file);

                Post post = postMap.get(chatId);
                if (post.getImage() == null) {
                    post.setImage(new ArrayList<>());
                }
                post.getImage().add(file);
                // Отправляем изображение обратно пользователю

            } catch (Exception e) {
                sendMessage(chatId, "Не удалось загрузить изображение ", getReturnToPostCreationKeyboard());
                e.printStackTrace();
            }
        }

        if (message != null && message.hasText()) {
            if (message.getText().equals("Завершить загрузку")) {
                sendMessage(chatId, "Укажите авторство, написав Ваше имя и фамилию, а также ваш ник в Telegram через символ @", getReturnToPostCreationKeyboard());
                userStateMap.put(chatId, State.ENTER_AUTHOR);
            }
        }
    }

    private void sendPhoto(String chatId, File file) throws TelegramApiException {
        SendPhoto sendPhoto = new SendPhoto();
        sendPhoto.setChatId(chatId);
        sendPhoto.setPhoto(new InputFile(file));  // Указываем файл для отправки

        execute(sendPhoto);  // Отправляем фото
    }

    private void handleEnterAuthor(long chatId, String messageText) {
        if (messageText.contains("@")) {
            Post post = postMap.get(chatId);
            post.setAuthor(messageText);
            sendMessage(chatId, "Успешно! Ваш пост отправлен на модерацию, ожидайте ответа!");
            userStateMap.put(chatId, State.AWAIT_MODERATION);
            sendPostToModerators(post, chatId);

            // Отправка кнопки для возврата к главному меню
            sendReturnToPostCreationButton(chatId);
        } else {
            sendMessage(chatId, "Проверте написание вашего ника,укажите через символ @");
        }
    }

    private void sendPostToModerators(Post post, long chatId) {
        SendMessage message = new SendMessage();
        message.setText(formatPost(post));

        for (Long moderatorChatId : moderatorChatIds) {
            message.setChatId(String.valueOf(moderatorChatId));
            executeMessage(message);
            if (!post.getImage().isEmpty()) {
                post.getImage().forEach(file -> {
                    try {
                        sendPhoto(message.getChatId(), file);
                    } catch (TelegramApiException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }

        cleanPost(chatId);

    }

    private void cleanPost(long chatId) {
        if (postMap.get(chatId) != null) {
            postMap.get(chatId).getImage().forEach(File::delete);
            postMap.remove(chatId);
        }

    }

    private String formatPost(Post post) {
        return "Тематика: " + post.getTheme() + "\n" +
                "Заголовок: " + post.getTitle() + "\n" +
                "Основной текст: " + post.getText() + "\n" +
                "Автор: " + post.getAuthor() + "\n"
                ;
    }

    private void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        executeMessage(message);
    }

    private void sendMessage(long chatId, String text, ReplyKeyboardMarkup keyboardMarkup) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setReplyMarkup(keyboardMarkup);
        executeMessage(message);
    }

    private void executeMessage(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private ReplyKeyboardMarkup getMainMenuKeyboard() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("Написать пост"));
        row1.add(new KeyboardButton("F.A.Q"));

        keyboard.add(row1);
        keyboardMarkup.setKeyboard(keyboard);
        keyboardMarkup.setResizeKeyboard(true);
        return keyboardMarkup;
    }

    private ReplyKeyboardMarkup getThemesKeyboard() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("HR"));
        row1.add(new KeyboardButton("Маркетинг"));
        row1.add(new KeyboardButton("в начало"));

        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("IT"));
        row2.add(new KeyboardButton("Другое"));

        keyboard.add(row1);
        keyboard.add(row2);
        keyboardMarkup.setKeyboard(keyboard);
        keyboardMarkup.setResizeKeyboard(true);
        return keyboardMarkup;
    }

    private ReplyKeyboardMarkup getYesNoKeyboard() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("Да"));
        row1.add(new KeyboardButton("Нет"));
        row1.add(new KeyboardButton("в начало"));

        keyboard.add(row1);
        keyboardMarkup.setKeyboard(keyboard);
        keyboardMarkup.setResizeKeyboard(true);
        return keyboardMarkup;
    }

    private ReplyKeyboardMarkup getEndAddImage() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("Завершить загрузку"));
        row1.add(new KeyboardButton("в начало"));

        keyboard.add(row1);
        keyboardMarkup.setKeyboard(keyboard);
        keyboardMarkup.setResizeKeyboard(true);
        return keyboardMarkup;
    }

    private ReplyKeyboardMarkup getReturnToPostCreationKeyboard() {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = new ArrayList<>();

        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("в начало"));

        keyboard.add(row1);
        keyboardMarkup.setKeyboard(keyboard);
        keyboardMarkup.setResizeKeyboard(true);
        return keyboardMarkup;
    }

    private void sendReturnToPostCreationButton(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("После модерации, Вы можете создать новый пост.");
        message.setReplyMarkup(getReturnToPostCreationKeyboard());
        executeMessage(message);
    }
}
