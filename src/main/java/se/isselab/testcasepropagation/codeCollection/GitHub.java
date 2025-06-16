/*
Copyright [2024] [Luca Kramer]

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package se.isselab.testcasepropagation.codeCollection;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import se.isselab.testcasepropagation.intelliJ.settings.TestCasePropagationSettings;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class GitHub {
    private final String accessToken;
    private final Map<String, Object> cache = new HashMap<>();
    private HttpClient client = HttpClients.createDefault();

    public GitHub(String accessToken){
        this.accessToken = accessToken;
    }
    public @NotNull List<String[]> fetchForks(String repository) {
        String repoUrl = "https://api.github.com/repos/" + repository +"/forks";

        List<String[]> forksList = new ArrayList<>();

        try {
            JSONArray forksArray = getPaginatedJsonArray(repoUrl);

            // Process each fork
            for (int i = 0; i < forksArray.length(); i++) {
                JSONObject fork = forksArray.getJSONObject(i);
                String[] forkData = new String[2];
                forkData[0] = fork.getString("full_name");
                forkData[1] = fork.getString("pushed_at");
                forksList.add(forkData);
                // You can further process the fork information here
            }

        } catch (Exception e) {
            e.printStackTrace();
            // Handle exceptions appropriately
            throw new RuntimeException(e);
        }
        return forksList;
    }

    public List<String> fetchAllFilePaths(String fork) {
        List<String> filePaths = new ArrayList<>();

        // GitHub API endpoint for listing repository contents
        String apiUrl = "https://api.github.com/repos/" + fork + "/contents";

        // Create HttpClient instance
        HttpClient httpClient = HttpClients.createDefault();
        HttpGet request = new HttpGet(apiUrl);
        request.addHeader("Authorization", "Bearer " + accessToken);
        request.addHeader("Accept", "application/vnd.github.v3+json");

        try {
            // Execute request
            HttpResponse response = httpClient.execute(request);

            // Get response entity
            HttpEntity entity = response.getEntity();
            String responseBody = EntityUtils.toString(entity);

            // Parse JSON response
            JSONArray jsonResponse = new JSONArray(responseBody);

            // Recursively fetch all file paths
            fetchFilePathsRecursively(jsonResponse, filePaths, httpClient, accessToken);

        } catch (IOException | org.json.JSONException e) {
            e.printStackTrace();
            // Handle exceptions appropriately
        }

        return filePaths;
    }

    private void fetchFilePathsRecursively(JSONArray jsonArray, List<String> filePaths, HttpClient httpClient, String accessToken) {
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject item = jsonArray.getJSONObject(i);
            String type = item.getString("type");
            String path = item.getString("path");

            if ("file".equals(type)) {
                filePaths.add(path);
            } else if ("dir".equals(type)) {
                String url = item.getString("url");
                try {
                    HttpGet request = new HttpGet(url);
                    request.addHeader("Authorization", "Bearer " + accessToken);
                    request.addHeader("Accept", "application/vnd.github.v3+json");

                    HttpResponse response = httpClient.execute(request);
                    HttpEntity entity = response.getEntity();
                    String responseBody = EntityUtils.toString(entity);

                    JSONArray subDirectory = new JSONArray(responseBody);
                    fetchFilePathsRecursively(subDirectory, filePaths, httpClient, accessToken);

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public String fetchFileContent(String repository, String filePath) {
        // Construct the API URL for fetching file content
        String apiUrl = "https://api.github.com/repos/" + repository + "/contents/" + filePath;

        // Create HttpClient instance
        HttpClient httpClient = HttpClients.createDefault();
        HttpGet request = new HttpGet(apiUrl);
        request.addHeader("Authorization", "Bearer " + accessToken);
        request.addHeader("Accept", "application/vnd.github.v3.raw");

        try {
            // Execute request
            HttpResponse response = httpClient.execute(request);

            // Get response entity
            HttpEntity entity = response.getEntity();
            String responseBody = EntityUtils.toString(entity);

            return responseBody;
            // You can further process the file content here
        } catch (IOException e) {
            e.printStackTrace();
            // Handle exceptions appropriately
        }
        return "";
    }

    public String[] fetchForkedOff(String repository) {
        // Construct the API URL for fetching repository information
        String apiUrl = "https://api.github.com/repos/" + repository;

        try {
            JSONObject jsonResponse = getJsonObject(apiUrl);

            // Check if the repository was forked from another repository
            if (jsonResponse.has("parent")) {
                JSONObject parentRepo = jsonResponse.getJSONObject("parent");
                String fullName = parentRepo.getString("full_name");
                String forkTime = jsonResponse.getString("pushed_at"); // Time the current repository (fork) was created
                return new String[] { fullName, forkTime };
            }

        } catch (Exception e) {
            e.printStackTrace();
            // Handle exceptions appropriately
        }

        return null; // If not forked from another repository
    }





    public Set<String> findModifiedFilesAfterCreation(String owner, String repo) throws IOException, InterruptedException {
        Instant creationDate = getRepoCreationDate(owner, repo);

        Set<String> modifiedFiles = new HashSet<>();
        String commitsUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/commits?since=" + creationDate.toString();

        JSONArray commits = getPaginatedJsonArray(commitsUrl);

        for (int i = 0; i < commits.length(); i++) {
            JSONObject commit = commits.getJSONObject(i);
            String sha = commit.getString("sha");
            modifiedFiles.addAll(getFilesInCommit(owner, repo, sha));
        }

        return modifiedFiles;
    }

    private Instant getRepoCreationDate(String owner, String repo) throws IOException, InterruptedException {
        JSONObject repoJson = getJsonObject("https://api.github.com/repos/" + owner + "/" + repo);
        return Instant.parse(repoJson.getString("created_at"));
    }

    private Set<String> getFilesInCommit(String owner, String repo, String sha) throws IOException, InterruptedException {
        JSONObject commitJson = getJsonObject("https://api.github.com/repos/" + owner + "/" + repo + "/commits/" + sha);
        Set<String> files = new HashSet<>();
        JSONArray fileArray = commitJson.getJSONArray("files");

        for (int i = 0; i < fileArray.length(); i++) {
            JSONObject file = fileArray.getJSONObject(i);
            String filename = file.getString("filename");
            if ("removed".equals(file.getString("status"))) continue;
            if (filename.endsWith(".java")) files.add(filename);
        }

        return files;
    }





    public List<String> fetchForkSelection(String repository) {
        // Step 1: Fetch all forks (parent, siblings, children)
        Set<String> candidateForks = null;
        try {
            candidateForks = fetchAllForks(repository);
        } catch (Exception e) {
            System.err.println("Failed to fetch forks: " + e);
            throw new RuntimeException(e);
        }

        // Step 2: Filter and collect metadata
        List<JSONObject> qualifiedForks = new ArrayList<>();
        for (String repo : candidateForks) {
            try {
                JSONObject repoJson = getJsonObject("https://api.github.com/repos/" + repo);

                // ASE paper checks
                String forkDefaultBranch = repoJson.getString("default_branch");
                if (repoJson.has("parent")) {
                    String parentRepo = repoJson.getJSONObject("parent").getString("full_name");
                    String parentDefaultBranch = getJsonObject("https://api.github.com/repos/" + parentRepo).getString("default_branch");

                    if (!hasAtLeast5ForkSpecificCommits(repoJson, parentDefaultBranch, forkDefaultBranch)) continue;
                    if (!hasAtLeast30DaysEvolution(repoJson, parentDefaultBranch, forkDefaultBranch)) continue;
                }
                if (wasDiscontinuedAfterMerge(repo)) continue;

                // Collect metadata
                JSONArray commits = getPaginatedJsonArray("https://api.github.com/repos/" + repo + "/commits");
                int contributorsCount = getPaginatedJsonArray("https://api.github.com/repos/" + repo + "/contributors").length();

                repoJson.put("commits", commits.length());
                repoJson.put("contributors", contributorsCount);

                qualifiedForks.add(repoJson);

            } catch (Exception e) {
                System.err.println("Skipping repo due to error: " + repo);
            }
        }

        // Step 3: Sort by commits, contributors, stars, forks
        qualifiedForks.sort((a, b) -> {
            int cmp = Integer.compare(b.getInt("commits"), a.getInt("commits"));
            if (cmp != 0) return cmp;
            cmp = Integer.compare(b.getInt("contributors"), a.getInt("contributors"));
            if (cmp != 0) return cmp;
            cmp = Integer.compare(b.getInt("stargazers_count"), a.getInt("stargazers_count"));
            if (cmp != 0) return cmp;
            return Integer.compare(b.getInt("forks_count"), a.getInt("forks_count"));
        });

        // Step 4: Return up to top 100
        return qualifiedForks.stream().limit(100).map(f -> f.getString("full_name")).toList();
    }

    public Set<String> fetchAllForks(String repository) throws IOException, InterruptedException {
        Set<String> forks = new HashSet<>();

        // Step 1: Find the parent
        JSONObject repo = getJsonObject("https://api.github.com/repos/" + repository);
        if (repo != null && repo.has("parent")) {
            String parentFullName = repo.getJSONObject("parent").getString("full_name");
            forks.add(parentFullName);

            // Step 2: Add siblings
            for (Object fork : getPaginatedJsonArray("https://api.github.com/repos/" + parentFullName + "/forks")) {
                forks.add(((JSONObject) fork).getString("full_name"));
            }
            forks.remove(repository);
        }

        // Step 3: Add children
        for (Object fork : getPaginatedJsonArray("https://api.github.com/repos/" + repository + "/forks")) {
            forks.add(((JSONObject) fork).getString("full_name"));
        }

        return forks;
    }

    private boolean hasAtLeast5ForkSpecificCommits(JSONObject repository, String parentDefaultBranch, String forkDefaultBranch) {
        try {
            // 1. Get parent info
            String parentFullName = repository.getJSONObject("parent").getString("full_name");

            String forkOwner = repository.getJSONObject("owner").getString("login");

            // 2. Get fork creation date
            Instant forkCreationDate = Instant.parse(repository.getString("created_at"));

            // 3. Rough prefilter: commits since creation
            JSONArray commits = getPaginatedJsonArray("https://api.github.com/repos/" + repository + "/commits?since=" + forkCreationDate.toString());
            if (commits.length() < 5) return false;

            // 4. Accurate fork-specific check with /compare/
            String compareUrl = "https://api.github.com/repos/" + parentFullName + "/compare/" + parentDefaultBranch + "..." + forkOwner + ":" + forkDefaultBranch;
            JSONObject compareJson = getJsonObject(compareUrl);
            int aheadBy = compareJson.getInt("ahead_by");

            return aheadBy >= 5;

        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasAtLeast30DaysEvolution(JSONObject repository, String parentDefaultBranch, String forkDefaultBranch) throws InterruptedException {
        try {
            String parentFullName = repository.getJSONObject("parent").getString("full_name");
            if (parentFullName == null) {
                JSONArray commits = getPaginatedJsonArray("https://api.github.com/repos/" + repository + "/commits");
                if (commits.length() < 2) return false;

                Instant first = Instant.parse(commits.getJSONObject(commits.length() - 1).getJSONObject("commit").getJSONObject("committer").getString("date"));
                Instant last = Instant.parse(commits.getJSONObject(0).getJSONObject("commit").getJSONObject("committer").getString("date"));

                return Duration.between(first, last).toDays() >= 30;
            } else {
                String forkOwner = repository.getJSONObject("owner").getString("login");

                // Compare parent...fork to get only fork-specific commits
                String compareUrl = "https://api.github.com/repos/" + parentFullName + "/compare/" + parentDefaultBranch + "..." + forkOwner + ":" + forkDefaultBranch;
                JSONObject compareJson = getJsonObject(compareUrl);

                if (!compareJson.has("commits")) return false;
                JSONArray forkCommits = compareJson.getJSONArray("commits");
                if (forkCommits.isEmpty()) return false;

                // Get the earliest and latest commit dates
                Instant earliest = Instant.MAX;
                Instant latest = Instant.MIN;

                for (int i = 0; i < forkCommits.length(); i++) {
                    JSONObject commit = forkCommits.getJSONObject(i).getJSONObject("commit");
                    String dateStr = commit.getJSONObject("committer").getString("date");
                    Instant date = Instant.parse(dateStr);

                    if (date.isBefore(earliest)) earliest = date;
                    if (date.isAfter(latest)) latest = date;
                }

                long days = Duration.between(earliest, latest).toDays();
                return days >= 30;
            }

        } catch (IOException e) {
            return false;
        }
    }

    private boolean wasDiscontinuedAfterMerge(String repository) {
        try {
            String commitsUrl = "https://api.github.com/repos/" + repository + "/commits";
            JSONArray commits = getJsonArray(commitsUrl);

            if (commits.isEmpty()) return false;

            JSONObject commit = commits.getJSONObject(0);
            boolean isMerge = commit.getJSONArray("parents").length() > 1;
            if (!isMerge) return false;

            String commitDateStr = commit
                    .getJSONObject("commit")
                    .getJSONObject("committer")
                    .getString("date");

            Instant lastCommitTime = Instant.parse(commitDateStr);
            long daysSinceLastCommit = Duration.between(lastCommitTime, Instant.now()).toDays();

            return daysSinceLastCommit > 180;

        } catch (Exception e) {
            return false;
        }
    }




    private HttpResponse sendGetRequest(String url) throws IOException, InterruptedException {
        HttpGet request = new HttpGet(url);
        request.addHeader("Authorization", "Bearer " + accessToken);
        request.addHeader("Accept", "application/vnd.github.v3+json");
        HttpResponse response = client.execute(request);

        if (response.getStatusLine().getStatusCode() == 403 &&
            response.containsHeader("X-RateLimit-Remaining") &&
            response.getFirstHeader("X-RateLimit-Remaining").getValue().equals("0")) {

            long resetTime = Long.parseLong(response.getFirstHeader("X-RateLimit-Reset").getValue());
            long waitTime = resetTime - Instant.now().getEpochSecond() + 1;
            if (waitTime > 0) {
                System.out.println("Rate limited. Sleeping for " + waitTime + " seconds.");
                Thread.sleep(waitTime * 1000);
                return sendGetRequest(url); // retry
            }
        }
        return response;
    }

    private JSONObject getJsonObject(String url) throws IOException, InterruptedException {
        if (cache.containsKey(url)) return (JSONObject) cache.get(url);

        HttpResponse response = sendGetRequest(url);
        String body = EntityUtils.toString(response.getEntity());
        JSONObject object = new JSONObject(body);

        cache.put(url, object);
        return object;
    }

    private JSONArray getJsonArray(String url) throws IOException, InterruptedException {
        if (cache.containsKey(url)) return (JSONArray) cache.get(url);

        HttpResponse response = sendGetRequest(url);
        String body = EntityUtils.toString(response.getEntity());
        JSONArray array = new JSONArray(body);

        cache.put(url, array);
        return array;
    }

    private JSONArray getPaginatedJsonArray(String baseUrl) throws IOException, InterruptedException {
        if (cache.containsKey(baseUrl)) return (JSONArray) cache.get(baseUrl);

        JSONArray all = new JSONArray();
        String url = baseUrl;
        while (url != null) {
            HttpResponse response = sendGetRequest(url);
            String body = EntityUtils.toString(response.getEntity());

            JSONArray array = new JSONArray(body);
            for (int i = 0; i < array.length(); i++) {
                all.put(array.get(i));
            }
            url = getNextPageLink(response);
        }

        cache.put(baseUrl, all);
        return all;
    }

    private String getNextPageLink(HttpResponse response) {
        Header linkHeader = response.getFirstHeader("Link");
        if (linkHeader == null) return null;

        for (String part : linkHeader.getValue().split(",")) {
            if (part.contains("rel=\"next\"")) {
                return part.substring(part.indexOf("<") + 1, part.indexOf(">"));
            }
        }
        return null;
    }

}
