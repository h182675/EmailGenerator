package services;

import model.EmailTemplate;
import model.TemplateFile;
import model.TemplateField;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateService {

    public TemplateService() {

    }

    public EmailTemplate parseEmailTemplateFile(TemplateFile file) throws FileNotFoundException {

        String templateText = createEmailTemplateText(file);
        String cleanTemplateText = cleanTemplateText(templateText);
        // Choice definitions
        Map<String, List<String>> choiceMap = new HashMap<>();
        if(hasDefinitions(templateText)) {
            choiceMap = getDefinitionsFromTemplateText(templateText);
        }
        // Handle fields - standard, large, permanent, multi-fields
        Map<String, TemplateField> templateTextFieldMap = parseTemplateFields(cleanTemplateText, choiceMap);
        // Permanent fields are handled on the frontend controller for now.
        EmailTemplate emailTemplate = new EmailTemplate(file.getPath(), file.getName(), cleanTemplateText, templateTextFieldMap);
        emailTemplate.setChoiceDefinitions(choiceMap);
        return emailTemplate;
    }

    private Map<String, TemplateField> templateTextFieldMap = new HashMap<>();
    private Map<String, TemplateField> parseTemplateFields(String templateText, Map<String, List<String>> choiceDefinitions) {

        // Standard fields & Choice fields
        parseStandardAndChoiceFields(templateText, choiceDefinitions);
        // Large fields
        parseLargeFields(templateText);

        return templateTextFieldMap;
    }

    private void parseLargeFields(String templateText) {
        Scanner scan = new Scanner(templateText);

        scan.findAll(Pattern.compile("<![a-zæøåA-ZÆØÅ0-9]+!>"))
                .forEach(item -> {
                    if (templateTextFieldMap.get(item.group()) == null) {
                        TemplateField templateField = new TemplateField(
                                item.end() - item.start(),
                                item.group(),
                                TemplateField.FieldType.LARGE_FIELD
                        );
                        templateField.addLocation(item.start());
                        templateTextFieldMap.put(item.group(), templateField);
                    }
                    templateTextFieldMap.get(item.group()).addLocation(item.start());
                });
        scan.close();
    }

        private void parseStandardAndChoiceFields(String templateText, Map<String, List<String>> choiceDefinitions) {
        Scanner scan = new Scanner(templateText);

        scan.findAll(Pattern.compile("<{2}[a-zæøåA-ZÆØÅ0-9 ]+>{2}"))
                .forEach(item -> {
                    if (choiceDefinitions.containsKey(item.group())) {
                        if(Objects.isNull(templateTextFieldMap.get(item.group()))){
                            TemplateField templateField = new TemplateField(
                                    item.end() - item.start(),
                                    item.group(),
                                    TemplateField.FieldType.CHOICE_FIELD,
                                    choiceDefinitions.get(item.group()));
                            templateTextFieldMap.put(item.group(), templateField);
                        }
                    } else if(templateTextFieldMap.get(item.group()) == null){
                        TemplateField templateField = new TemplateField(
                                item.end() - item.start(),
                                item.group(),
                                TemplateField.FieldType.STANDARD_FIELD);
                        templateField.addLocation(item.start());
                        templateTextFieldMap.put(item.group(), templateField);
                    }
                    templateTextFieldMap.get(item.group()).addLocation(item.start());
                });
        scan.close();
    }

    private boolean hasDefinitions(String templateText) {
        Scanner scan = new Scanner(templateText);
        boolean hasDefinition = scan.nextLine().contains("#def");
        scan.close();
        return hasDefinition;
    }

    private Map<String, List<String>> getDefinitionsFromTemplateText(String templateText) {
        Scanner scan = new Scanner(templateText);
        String nextLine = scan.nextLine();
        Map<String, List<String>> choiceMap = new HashMap<>();
        while (scan.hasNextLine() && !nextLine.contains("#enddef")) {
            if(!nextLine.contains("#def")) {
                Scanner lineScanner = new Scanner(nextLine);
                String name = lineScanner.findInLine(Pattern.compile("<{2}[a-zæøåA-ZÆØÅ0-9 ]+>{2}"));
                choiceMap.put(name, parseChoiceDefinitions(nextLine));
            }
            nextLine = scan.nextLine();
        }
        scan.close();
        return choiceMap;
    }

    private List<String> parseChoiceDefinitions(String line) {
        Scanner scan = new Scanner(line);
        List<String> choices = new ArrayList<>();
        String choicesString = scan.findInLine(Pattern.compile("=[a-zæøåA-ZÆØÅ0-9 ,.@]+"));
        Arrays.stream(choicesString.substring(1).split(",")).forEach(item -> {
            int index = 0;
            for (char a : item.toCharArray()) {
                if (a != ' ') {
                    break;
                } else {
                    index++;
                }
            }
            choices.add(item.substring(index));
        });
        scan.close();
        return choices;
    }

    private String createEmailTemplateText(TemplateFile templateFile) throws FileNotFoundException {
        Scanner scan = new Scanner(templateFile);
        StringBuilder emailStringBuilder = new StringBuilder();
        while (scan.hasNext()) {
            emailStringBuilder.append(scan.nextLine());
            if (scan.hasNextLine()) {
                emailStringBuilder.append("\n");
            }
        }
        scan.close();
        return emailStringBuilder.toString();
    }

    private String cleanTemplateText(String templateText) {
        Scanner scan = new Scanner(templateText);
        StringBuilder stringBuilder = new StringBuilder();
        boolean afterDefinition = false;
        while (scan.hasNextLine()) {
            if (afterDefinition) {
                stringBuilder.append(scan.nextLine());
                if (scan.hasNextLine()) {
                    stringBuilder.append("\n");
                }
            } else if (scan.nextLine().contains("#end")) {
                afterDefinition = true;
            }
        }
        scan.close();
        return stringBuilder.toString();
    }

    public ArrayList<TemplateFile> listTemplateFiles(File folder) {
        ArrayList<TemplateFile> templateFileList = new ArrayList<>();
        for (final File entryFile : Objects.requireNonNull(folder.listFiles())) {
            Optional<String> optExt = getFileExtensionFromName(entryFile.getName());
            if (optExt.isPresent() && optExt.get().equals("template")) {
                templateFileList.add(new TemplateFile(entryFile.getPath()));
            }
        }
        return templateFileList;
    }

    private Optional<String> getFileExtensionFromName(String fileName) {
        return Optional.ofNullable(fileName)
                .filter(f -> f.contains("."))
                .map(f -> f.substring(fileName.lastIndexOf(".") + 1));
    }

    public Map<String, TemplateField> parseConfigFile(File file) throws FileNotFoundException {
        Scanner fileScan = new Scanner(new BufferedReader(new FileReader(file)));
        StringBuilder fileSb = new StringBuilder();
        while (fileScan.hasNextLine()){
            fileSb.append(fileScan.nextLine()).append("\n");
        }
        fileScan.close();
        String fileString = fileSb.toString();
        Scanner stringScan = new Scanner(fileString);
        Map<String, TemplateField> permSettingsMap = new HashMap<>();
        // Get keys and values
        String nextLine;
        Pattern p = Pattern.compile("<{2}\\$[a-zæøåA-ZÆØÅ0-9]+>{2}");
        Matcher m;
        while(stringScan.hasNextLine()) {
            nextLine = stringScan.nextLine();
            m = p.matcher(nextLine);
            if (m.find()) {
                String text = parseConfigFieldValue(nextLine);
                permSettingsMap.put(m.group(), new TemplateField(text.length(),text, TemplateField.FieldType.PERM_FIELD));
            }
        }
        stringScan.close();
        return permSettingsMap;
    }

    private String parseConfigFieldValue(String line) {
        int index = 0;
        for(char a : line.toCharArray()) {
            if (a == '=') {
                index++;
                break;
            }
            index++;
        }
        return line.substring(index);
    }
}
