package lk.ijse.chatApp.controller;

import com.gluonhq.emoji.Emoji;
import com.gluonhq.emoji.EmojiData;
import com.gluonhq.emoji.util.TextUtils;
import io.github.palexdev.materialfx.controls.MFXButton;
import io.github.palexdev.mfxcore.utils.fx.SwingFXUtils;
import javafx.application.Platform;
import javafx.collections.MapChangeListener;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import lk.ijse.chatApp.util.UserCountUtil;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ChatFormController {


    String message;
    @FXML
    private ScrollPane scrollPane;
    @FXML
    private TextField txtMassage;
    @FXML
    private Text txtMemberCount;
    @FXML
    private Circle circleImg;
    @FXML
    private Text txtName;
    @FXML
    private VBox peopleVbox;
    @FXML
    private AnchorPane peoplePane;
    @FXML
    private VBox vBox;
    @FXML
    private GridPane emojiPickerGrid;
    private String name;
    private Socket socket;
    private DataInputStream inputStream;
    private DataOutputStream outputStream;

    public void initialize() {

        setScrollPaneTransparent();
        setChatNameAndProfilePic();
        setEmojis();
        setUserCount();
        updateContacts();


        //This line is to auto scroll down when new Message is received
        vBox.heightProperty().addListener((observableValue, oldValue, newValue) -> scrollPane.setVvalue((Double) newValue));

        //Set Listener to Observable Map to update the usercount
        UserCountUtil.users.addListener((MapChangeListener<String, Image>) change -> {
            setUserCount();
            updateContacts();
        });


        //idk wtf is this but apparently its a another lambda expression
        //Original code was
        //  Runnable runnable = ()->{socketInitialize();};
        Runnable runnable = this::socketInitialize;
        Thread thread = new Thread(runnable);
        thread.start();
    }

    private void socketInitialize() {
        try {
            socket = new Socket("localhost", 3030);
            inputStream = new DataInputStream(socket.getInputStream());
            outputStream = new DataOutputStream(socket.getOutputStream());

            // Client Connected msg
            outputStream.writeUTF("noti&" + this.name + "& Connected");
            outputStream.flush();

            do {
                try {
                    message = inputStream.readUTF();
                    Platform.runLater(() -> {
                        messageSelector(message);
                    });
                } catch (EOFException e) {
                    System.out.println("Connection closed by server.");
                    break;
                }
            } while (!message.equals("end"));
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void updateContacts() {
        Platform.runLater(() -> {
            peopleVbox.getChildren().clear();
            peopleVbox.setSpacing(10);

            for (Map.Entry<String, Image> entry : UserCountUtil.users.entrySet()) {
                String userName = entry.getKey();
                Image userImage = entry.getValue();
                VBox vbox = new VBox();
                vbox.setAlignment(Pos.CENTER);
                vbox.setSpacing(10);
                Circle circle = new Circle(25);
                circle.setStroke(Color.BLACK);
                circle.setStrokeWidth(2);
                circle.setFill(new ImagePattern(userImage));
                Label label = new Label(userName);
                vbox.getChildren().addAll(circle, label);
                peopleVbox.getChildren().add(vbox);
            }
        });
    }
    private void setEmojis() {
        //Clear the grid
        emojiPickerGrid.getChildren().clear();

        String text = "grinning grin joy smile smiling_face_with_tear sunglasses heart pinched_fingers wave";
        String[] words = text.split(" ");


        List<Node> nodes = TextUtils.convertToTextAndImageNodes(createUnicodeText(text));

        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            MFXButton btn = new MFXButton(words[i], node);
            btn.setPrefHeight(27);
            btn.setPrefWidth(27);
            btn.setOnMouseClicked(mouseEvent -> {
                sendEmoji(btn.getText());
            });
            btn.setEllipsisString("");
            emojiPickerGrid.add(btn, i % 3, i / 3);
            GridPane.setHalignment(btn, javafx.geometry.HPos.CENTER);
            GridPane.setValignment(btn, javafx.geometry.VPos.CENTER);

        }
    }

    private void sendEmoji(String text) {

        try {
            outputStream.writeUTF("emoji&" + this.name + "&" + text);
            outputStream.flush();

            List<Node> nodes = TextUtils.convertToTextAndImageNodes(createUnicodeText(text));
            HBox hBox = new HBox();
            hBox.setPadding(new Insets(5, 5, 5, 10));
            hBox.setAlignment(Pos.BASELINE_RIGHT);
            hBox.getChildren().add(nodes.get(0));
            vBox.getChildren().add(hBox);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }



    private void messageSelector(String message) {

        String[] msg = message.split("&");
        String type = msg[0];
        String sender = msg[1];
        String contain = msg[2];


        switch (type) {
            case "noti":
                notification(sender + contain);
                break;
            case "msg":
                receiveMassage(sender, contain);
                break;
            case "img":
                receivedName(sender);
                receiveImage(contain);
                break;
            case "emoji":
                receivedName(sender);
                receiveEmoji(contain);
                break;
        }

    }

    private void receiveEmoji(String contain) {
        List<Node> nodes = TextUtils.convertToTextAndImageNodes(createUnicodeText(contain));
        HBox hBox = new HBox();
        hBox.setPadding(new Insets(5, 5, 5, 10));
        hBox.setAlignment(Pos.BASELINE_LEFT);
        hBox.getChildren().add(nodes.get(0));
        vBox.getChildren().add(hBox);
    }

    private void receiveImage(String imageString) {
        try {
            Image image = convertStringToImage(imageString);
            ImageView imageView = new ImageView(image);
            imageView.setFitHeight(200);
            imageView.setFitWidth(200);
            HBox hBox = new HBox();
            hBox.setAlignment(Pos.BASELINE_LEFT);
            hBox.setPadding(new Insets(5, 5, 5, 10));
            hBox.getChildren().add(imageView);

            Platform.runLater(() -> {
                vBox.getChildren().add(hBox);
            });
        }
        catch (IOException e){

        }
    }

    private void receivedName(String sender) {

        HBox hBox = new HBox();
        hBox.setAlignment(Pos.BASELINE_LEFT);

        Text senderText = new Text(sender);
        senderText.setFont(Font.font("Arial", FontWeight.BLACK, 12));
        hBox.setPadding(new Insets(5, 5, 5, 10));
        TextFlow textFlow = new TextFlow(senderText);
        textFlow.setStyle(
                "-fx-color: rgb(239, 242, 255);" +
                        "-fx-background-color: rgb(255,255,255);" +
                        "-fx-background-radius: 20px;");

        textFlow.setPadding(new Insets(5, 20, 5, 10));
        textFlow.setLineSpacing(5);
        hBox.getChildren().add(textFlow);
        vBox.getChildren().add(hBox);
    }


    private void setUserCount() {
        Platform.runLater(() -> {
            String count = String.valueOf(UserCountUtil.users.size());
            txtMemberCount.setText(count);
        });
    }

    public void setName(String name) {
        this.name = name;
    }

    private void setChatNameAndProfilePic() {
        Platform.runLater(() -> {

            //Set Name
            txtName.setText(this.name);


            //Set Image
            Image profileImage = UserCountUtil.users.get(name);
            circleImg.setFill(new ImagePattern(profileImage));

        });
    }

    private void setScrollPaneTransparent() {
        Platform.runLater(() -> {
            scrollPane.setStyle("-fx-background-color: transparent; -fx-background-insets: 0; -fx-padding: 0;");
            scrollPane.lookup(".viewport").setStyle("-fx-background-color: transparent;");
            scrollPane.lookup(".scroll-bar").setStyle("-fx-background-color: transparent;");
            scrollPane.lookup(".scroll-bar:vertical").setStyle("-fx-background-color: transparent;");

        });
    }


    @FXML
    void btnSendOnAction(MouseEvent event) {
        sendMassage();
    }


    @FXML
    void txtFieldOnAction(ActionEvent event) {
        sendMassage();
    }

    private void sendMassage() {
        try {
            String massage = txtMassage.getText();


            outputStream.writeUTF("msg&" + this.name + "&" + massage);
            outputStream.flush();

            HBox hBox = new HBox();
            hBox.setAlignment(Pos.BASELINE_RIGHT);

            hBox.setPadding(new Insets(5, 5, 5, 10));
            Text text = new Text(massage);
            TextFlow textFlow = new TextFlow(text);
            textFlow.setStyle(
                    "-fx-color: rgb(239, 242, 255);" +
                            "-fx-background-color: rgb(15, 125, 242);" +
                            "-fx-background-radius: 20px;");

            textFlow.setPadding(new Insets(5, 10, 5, 10));
            text.setFill(Color.color(0.934, 0.925, 0.996));

            hBox.getChildren().add(textFlow);
            vBox.getChildren().add(hBox);

            txtMassage.clear();
        } catch (IOException e) {
            new Alert(Alert.AlertType.ERROR, e.getMessage()).show();
        }
    }

    private void notification(String massage) {

        HBox hBox = new HBox();
        hBox.setAlignment(Pos.CENTER);

        hBox.setPadding(new Insets(5, 5, 5, 10));
        Text text = new Text(massage);
        text.setFont(Font.font("Arial", FontWeight.BLACK, 13));
        TextFlow textFlow = new TextFlow(text);
        textFlow.setStyle(
                "-fx-color: rgb(239, 242, 255);" +
                        "-fx-background-color: rgb(255,255,255);" +
                        "-fx-background-radius: 5px;");

        textFlow.setPadding(new Insets(5, 10, 5, 10));
        text.setFill(Color.rgb(84, 83, 83));

        hBox.getChildren().add(textFlow);
        vBox.getChildren().add(hBox);
    }

    private void receiveMassage(String sender, String massage) {


        HBox hBox = new HBox();
        hBox.setAlignment(Pos.BASELINE_LEFT);

        Text senderText = new Text(sender + "\n");
        senderText.setFont(Font.font("Arial", FontWeight.BLACK, 12));


        hBox.setPadding(new Insets(5, 5, 5, 10));
        Text text = new Text(massage);
        TextFlow textFlow = new TextFlow(senderText, text);
        textFlow.setStyle(
                "-fx-color: rgb(239, 242, 255);" +
                        "-fx-background-color: rgb(255,255,255);" +
                        "-fx-background-radius: 20px;");

        textFlow.setPadding(new Insets(5, 20, 5, 10));
        textFlow.setLineSpacing(5);
        text.setFill(Color.rgb(0, 0, 0));

        hBox.getChildren().add(textFlow);
        vBox.getChildren().add(hBox);

    }


    @FXML
    void btnAddUserOnAction(ActionEvent event) throws IOException {
        Parent rootNode = FXMLLoader.load(this.getClass().getResource("/view/loginForm.fxml"));
        Scene scene = new Scene(rootNode);
        Stage stage = new Stage();
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
    }

    @FXML
    void btnLogOutOnAction(ActionEvent event) throws IOException {

        //Remove User
        UserCountUtil.users.remove(this.name);

        //Client DC message
        outputStream.writeUTF("noti&" + this.name + "& Disconnected");
        outputStream.flush();

        loadCreateAccountForm();
        closeWindow();
    }

    private void loadCreateAccountForm() throws IOException {
        Parent rootNode = FXMLLoader.load(this.getClass().getResource("/view/loginForm.fxml"));
        Scene scene = new Scene(rootNode);
        Stage stage = new Stage();
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
    }

    private void closeWindow() {
        Stage stage = (Stage) txtMassage.getScene().getWindow();
        stage.close();
    }


    @FXML
    void imageOnAction(MouseEvent event) throws IOException {

        FileChooser fileChooser = new FileChooser();
        configureFileChooser(fileChooser);

        Window window = ((Node) event.getTarget()).getScene().getWindow();
        File file = fileChooser.showOpenDialog(window);


        if (file != null) {
            ImageView imageView = new ImageView(file.toURI().toString());
            String imgText = convertImageToString(imageView.getImage());
            sendImage(imgText);
        }

    }

    private Image convertStringToImage(String imageAsString) throws IOException {
        byte[] imageBytes = Base64.getDecoder().decode(imageAsString);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(imageBytes);
        return new Image(inputStream);
    }

    private String convertImageToString(Image image) throws IOException {
        double maxWidth = 600;
        double maxHeight = 400;
        double width = image.getWidth();
        double height = image.getHeight();

        if (width > maxWidth || height > maxHeight) {
            double scaleFactor = Math.min(maxWidth / width, maxHeight / height);
            width *= scaleFactor;
            height *= scaleFactor;
        }

        BufferedImage resizedImage = new BufferedImage((int) width, (int) height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resizedImage.createGraphics();
        g.drawImage(SwingFXUtils.fromFXImage(image, null), 0, 0, (int) width, (int) height, null);
        g.dispose();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(resizedImage, "jpg", outputStream);

        byte[] imageBytes = outputStream.toByteArray();
        return Base64.getEncoder().encodeToString(imageBytes);
    }

    private void sendImage(String imageString) {

        try {
        Image image = convertStringToImage(imageString);
        ImageView imageView = new ImageView(image);
        imageView.setFitHeight(200);
        imageView.setFitWidth(200);

        HBox hBox = new HBox();
        hBox.setPadding(new Insets(5, 5, 5, 10));
        hBox.getChildren().add(imageView);
        hBox.setAlignment(Pos.CENTER_RIGHT);

        vBox.getChildren().add(hBox);
        outputStream.writeUTF("img&" + this.name + "&" + imageString);
        outputStream.flush();

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void configureFileChooser(FileChooser fileChooser) {
        fileChooser.setTitle("Select Image File");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.gif", "*.jpeg")
        );
    }


    @FXML
    void emojiOnAction(MouseEvent event) {
        emojiPickerGrid.setVisible(!emojiPickerGrid.isVisible());
    }


    private String createUnicodeText(String nv) {
        StringBuilder unicodeText = new StringBuilder();
        String[] words = nv.split(" ");
        for (String word : words) {
            Optional<Emoji> optionalEmoji = EmojiData.emojiFromShortName(word);
            if (optionalEmoji.isPresent()) {
                unicodeText.append(optionalEmoji.get().character());
            }
        }
        return unicodeText.toString();
    }

    @FXML
    void btnPeopleOnAction(ActionEvent event) {
        if (!peoplePane.isVisible()) {
            peoplePane.setVisible(true);
        }
    }


    @FXML
    void btnChatOnAction(ActionEvent event) {
        if (peoplePane.isVisible()) {
            peoplePane.setVisible(false);
        }
    }


}

