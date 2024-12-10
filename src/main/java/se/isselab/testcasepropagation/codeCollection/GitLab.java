package se.isselab.testcasepropagation.codeCollection;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GitLab {
    private String accessToken;

    public GitLab(String accessToken) {
        this.accessToken = accessToken;
    }

    public List<String[]> fetchForks(String projectId) {
        List<String[]> forksList = new ArrayList<>();
        String apiUrl = "https://gitlab.com/api/v4/projects/" + projectId + "/forks";
        HttpClient httpClient = HttpClients.createDefault();

        try {
            int page = 1;
            while (true) {
                HttpGet request = new HttpGet(apiUrl + "?page=" + page + "&per_page=100");
                request.addHeader("PRIVATE-TOKEN", accessToken);

                HttpResponse response = httpClient.execute(request);
                if (response.getStatusLine().getStatusCode() != 200) {
                    break;
                }

                HttpEntity entity = response.getEntity();
                String responseBody = EntityUtils.toString(entity);
                JSONArray forksArray = new JSONArray(responseBody);

                if (forksArray.length() == 0) {
                    break;
                }

                for (int i = 0; i < forksArray.length(); i++) {
                    JSONObject fork = forksArray.getJSONObject(i);
                    String forkId = fork.getString("id");
                    String lastActivityAt = fork.getString("last_activity_at");
                    forksList.add(new String[]{forkId, lastActivityAt});
                }

                page++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return forksList;
    }

    public List<String> fetchAllFilePaths(String projectId) {
        List<String> filePaths = new ArrayList<>();
        fetchFilePathsRecursively(projectId, null, filePaths);
        return filePaths;
    }

    private void fetchFilePathsRecursively(String projectId, String path, List<String> filePaths) {
        String apiUrl = "https://gitlab.com/api/v4/projects/" + projectId + "/repository/tree";
        if (path != null) {
            apiUrl += "?path=" + path;
        }

        HttpClient httpClient = HttpClients.createDefault();
        try {
            HttpGet request = new HttpGet(apiUrl);
            request.addHeader("PRIVATE-TOKEN", accessToken);

            HttpResponse response = httpClient.execute(request);
            if (response.getStatusLine().getStatusCode() != 200) {
                return;
            }

            HttpEntity entity = response.getEntity();
            String responseBody = EntityUtils.toString(entity);
            JSONArray treeArray = new JSONArray(responseBody);

            for (int i = 0; i < treeArray.length(); i++) {
                JSONObject item = treeArray.getJSONObject(i);
                String type = item.getString("type");
                String filePath = item.getString("path");

                if ("blob".equals(type)) {
                    filePaths.add(filePath);
                } else if ("tree".equals(type)) {
                    fetchFilePathsRecursively(projectId, filePath, filePaths);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String fetchFileContent(String projectId, String filePath) {
        String apiUrl = "https://gitlab.com/api/v4/projects/" + projectId + "/repository/files/" + filePath.replace("/", "%2F") + "/raw";
        HttpClient httpClient = HttpClients.createDefault();
        try {
            HttpGet request = new HttpGet(apiUrl);
            request.addHeader("PRIVATE-TOKEN", accessToken);

            HttpResponse response = httpClient.execute(request);
            if (response.getStatusLine().getStatusCode() != 200) {
                return "";
            }

            HttpEntity entity = response.getEntity();
            return EntityUtils.toString(entity);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    public String fetchForkedFrom(String projectId) {
        String apiUrl = "https://gitlab.com/api/v4/projects/" + projectId;
        HttpClient httpClient = HttpClients.createDefault();
        try {
            HttpGet request = new HttpGet(apiUrl);
            request.addHeader("PRIVATE-TOKEN", accessToken);

            HttpResponse response = httpClient.execute(request);
            if (response.getStatusLine().getStatusCode() != 200) {
                return null;
            }

            HttpEntity entity = response.getEntity();
            String responseBody = EntityUtils.toString(entity);
            JSONObject projectInfo = new JSONObject(responseBody);

            if (projectInfo.has("forked_from_project")) {
                JSONObject parentProject = projectInfo.getJSONObject("forked_from_project");
                return parentProject.getString("id");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}