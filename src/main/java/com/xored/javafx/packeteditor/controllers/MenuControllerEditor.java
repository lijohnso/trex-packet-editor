package com.xored.javafx.packeteditor.controllers;

import com.google.common.eventbus.EventBus;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.xored.javafx.packeteditor.data.PacketEditorModel;
import com.xored.javafx.packeteditor.data.user.DocumentFile;
import com.xored.javafx.packeteditor.events.NeedToUpdateTemplateMenu;
import com.xored.javafx.packeteditor.events.ProtocolExpandCollapseEvent;
import com.xored.javafx.packeteditor.metatdata.ProtocolMetadata;
import com.xored.javafx.packeteditor.service.ConfigurationService;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MenuControllerEditor implements Initializable {

    public static final String EXIT_MENU_ITEM = "exit";
    private Logger logger= LoggerFactory.getLogger(MenuControllerEditor.class);

    @Inject
    private FieldEditorController controller;

    @Inject
    ConfigurationService configurations;

    @Inject
    private EventBus eventBus;

    @FXML
    MenuBar applicationMenu;

    @FXML
    Menu fileMenu;
    
    @FXML
    Menu newTemplateMenu;
    
    @FXML
    MenuButton newTemplateMenuButton;

    @FXML
    Menu debugMenu;

    @FXML
    Button binaryModeOnBtn;

    @FXML
    Button abstractModeOnBtn;

    @FXML
    ComboBox<ProtocolMetadata> protocolSelector;
    
    @FXML
    Button appendProtocolBtn;
    
    @Inject
    @Named("resources")
    ResourceBundle resourceBundle;

    @Inject
    AppController appController;
    
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        initTemplateMenu();

        if (System.getenv("DEBUG") != null) {
            debugMenu.setVisible(true);
            binaryModeOnBtn.setVisible(true);
            abstractModeOnBtn.setVisible(true);
        }
    }

    public void initTemplateMenu() {
        if (newTemplateMenu != null) {
            newTemplateMenu.getItems().clear();
            addTemplates(newTemplateMenu.getItems());
        }
        if (newTemplateMenuButton != null) {
            newTemplateMenuButton.getItems().clear();
            addTemplates(newTemplateMenuButton.getItems());
        }
    }

    private void addTemplates(ObservableList<MenuItem> menuItems) {
        // Predefined templates from scapy server
        List<String> templates = controller.getTemplates();

        if (templates !=null && templates.size() > 0) {
            for (String t : templates) {
                int index = t.lastIndexOf('.');
                if (index != -1) {
                    t = t.substring(0, index);
                }
                MenuItem menuItem = new MenuItem(t);
                menuItem.setOnAction(event -> {
                    try {
                        String t64 = controller.getTemplate(menuItem.getText());
                        controller.getModel().loadDocumentFromJSON(t64);
                    } catch (Exception e) {
                        logger.error(e.getMessage());
                    }
                });
                menuItems.add(menuItem);
            }
        }

        // Add templates from user dir to menu list
        try {
            File repo = new File(configurations.getTemplatesLocation());
            if (repo.isDirectory()) {
                File[] fileList = getFnames(repo.getAbsolutePath());
                if (fileList.length > 0 && templates != null && templates.size() > 0) {
                    menuItems.add(new SeparatorMenuItem());
                }
                for (File f : fileList) {
                    String repoName = repo.getCanonicalPath();
                    String fileName = f.getCanonicalPath();

                    if (!f.exists()) {
                        break;
                    }
                    if (!isFilenameValid(repoName, fileName)) {
                        logger.error("File name '" + fileName + "' is invalid");
                        break;
                    }
                    if (!fileName.startsWith(repoName)) {
                        break;
                    }
                    fileName = fileName.replace(repoName, "");
                    if (fileName.startsWith(repoName)) {
                        break;
                    }
                    if(fileName.startsWith(File.separator)) {
                        fileName = fileName.substring(1);
                    }
                    if (!fileName.endsWith(DocumentFile.FILE_EXTENSION)) {
                        break;
                    }
                    int index = fileName.lastIndexOf(DocumentFile.FILE_EXTENSION);
                    if (index == -1) {
                        break;
                    }
                    fileName = fileName.substring(0, index).replace(File.separatorChar, '/');

                    MenuItem menuItem = new MenuItem(fileName);
                    menuItem.setOnAction(event -> {
                        try {
                            controller.getModel().loadDocumentFromFile(f.getCanonicalFile());
                        } catch (IOException e) {
                            logger.error(e.getMessage());
                        }
                    });
                    menuItems.add(menuItem);
                }
            }
        } catch (IOException e) {
            logger.warn("Exception was thrown while reading user's template dir: " + e.getMessage());
        }

        // Add "Save as template..." item
        menuItems.add(new SeparatorMenuItem());
        MenuItem menuItem = new MenuItem("Save as template...");
        menuItem.setOnAction(event -> {
            if (controller.getModel().getUserModel().getProtocolStack().isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setHeaderText("Save as template");
                alert.setContentText("Can't save empty template. \nPlease add at least one protocol.");
                alert.showAndWait();
                return;
            }
            try {
                File repo = new File(configurations.getTemplatesLocation());
                boolean tryagain = true;
                while (tryagain) {
                    String templ = controller.createNewTemplateDialog();
                    if (templ != null) {
                        templ = templ.replace('/', File.separatorChar);
                        if (isFilenameValid(repo.getCanonicalPath(), templ)) {
                            File file = new File(repo.getCanonicalPath(), templ + DocumentFile.FILE_EXTENSION);
                            boolean ok2write = true;
                            if (file.exists()) {
                                ok2write = controller.createFileOverwriteDialog(file);
                            }
                            if (ok2write) {
                                File dir = new File(file.getParent());
                                if (!dir.exists()) {
                                    dir.mkdirs();
                                }
                                controller.getModel().saveDocumentToFile(file);
                                eventBus.post(new NeedToUpdateTemplateMenu());
                                tryagain = false;
                            }
                        } else {
                            Alert alert = new Alert(Alert.AlertType.ERROR);
                            alert.setHeaderText("Save as template");
                            alert.setContentText("File name '" + templ + "' is invalid for local filesystem.\nPlease change file name and try again.");
                            alert.showAndWait();
                        }
                    }
                }
            } catch (Exception e) {
                logger.error(e.getMessage());
            }
        });
        menuItems.add(menuItem);
    }

    @FXML
    private void handleCloseAction() {
        appController.shutDown();
    }

    @FXML
    public void handleDeleteProtocolAction(ActionEvent actionEvent) {
        getModel().removeLast();
    }

    private PacketEditorModel getModel() { return controller.getModel(); }

    @FXML
    public void handleNewDocument(ActionEvent actionEvent) {
        getModel().newPacket();
    }

    @FXML
    public void handleOpenAction(ActionEvent actionEvent) {
        controller.showLoadDialog();
    }

    @FXML
    public void handleSaveAction(ActionEvent event) {
        controller.showSaveDialog();
    }

    @FXML
    public void handleRecalculateValues(ActionEvent actionEvent) {
        getModel().clearAutoFields();
    }

    @FXML
    public void handleUndo(ActionEvent actionEvent) {
        getModel().undo();
    }

    @FXML
    public void handleRedo(ActionEvent actionEvent){
        getModel().redo();
    }

    @FXML
    public void handleModeBinary(ActionEvent actionEvent) {
        getModel().setBinaryMode(true);
    }

    @FXML
    public void handleModeAbstract(ActionEvent actionEvent) {
        getModel().setBinaryMode(false);
    }

    @FXML
    public void handleCopyInstructions(ActionEvent event) {
        controller.copyInstructionsToClipboard();
    }
    
    public void handleExpandAll(ActionEvent actionEvent) {
        eventBus.post(new ProtocolExpandCollapseEvent(ProtocolExpandCollapseEvent.Action.EXPAND_ALL));
    }

    public void handleCollapseAll(ActionEvent actionEvent) {
        eventBus.post(new ProtocolExpandCollapseEvent(ProtocolExpandCollapseEvent.Action.COLLAPSE_ALL));
    }

    private static File[] getFnames(String sDir){
        File[] faFiles = new File(sDir).listFiles();
        ArrayList<File> res = new ArrayList<File>();

        for(File file: faFiles){
            if(file.isFile() && file.getName().toLowerCase().endsWith(".trp")){
                res.add(file);
            }
            if(file.isDirectory()){
                File[] faFiles2 = getFnames(sDir + "/" + file.getName());
                for (File f: faFiles2) {
                    res.add(f);
                }
            }
        }
        return res.toArray(new File[0]);
    }

    private static boolean isFilenameValid(String parent, String file) {
        try {
            File f = new File(parent);
            Path parentPath = f.toPath();
            Path filePath = parentPath.resolve(file);
            return true;
        }
        catch (Exception e) {
            ;
        }
        return false;
    }

}
