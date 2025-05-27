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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GitHub {
    private String accessToken;
    public GitHub(String accessToken){
        this.accessToken = accessToken;
    }
    public @NotNull List<String[]> fetchForks(String repository) {
        String repoUrl = "https://api.github.com/repos/" + repository +"/forks";

        // Create HttpClient instance
        HttpClient httpClient = HttpClients.createDefault();

        List<String[]> forksList = new ArrayList<>();

        try {
            int page = 1;
            while (true) {
                // Prepare request with pagination
                HttpGet request = new HttpGet(repoUrl + "?page=" + page);
                request.addHeader("Authorization", "Bearer " + accessToken);
                request.addHeader("Accept", "application/vnd.github.v3+json");

                // Execute request
                HttpResponse response = httpClient.execute(request);

                // Check for successful response
                if (response.getStatusLine().getStatusCode() != 200) {
                    System.err.println("Failed to fetch forks: " + response.getStatusLine());
                    break;
                }

                // Get response entity
                HttpEntity entity = response.getEntity();
                String responseBody = EntityUtils.toString(entity);

                // Parse JSON response
                JSONArray forksArray = new JSONArray(responseBody);

                // Break if no more forks are available
                if (forksArray.length() == 0) {
                    break;
                }

                // Process each fork
                for (int i = 0; i < forksArray.length(); i++) {
                    JSONObject fork = forksArray.getJSONObject(i);
                    String forkFullName = fork.getString("full_name");
                    String pushedAt = fork.getString("pushed_at");
                    String[] forkData = new String[2];
                    forkData[0] = forkFullName;
                    forkData[1] = pushedAt;
                    forksList.add(forkData);
                    // You can further process the fork information here
                }

                // Move to the next page
                page++;
            }
        } catch (IOException | org.json.JSONException e) {
            e.printStackTrace();
            // Handle exceptions appropriately
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
            JSONObject jsonResponse = new JSONObject(responseBody);

            // Check if the repository was forked from another repository
            if (jsonResponse.has("parent")) {
                JSONObject parentRepo = jsonResponse.getJSONObject("parent");
                String fullName = parentRepo.getString("full_name");
                String forkTime = jsonResponse.getString("pushed_at"); // Time the current repository (fork) was created
                return new String[] { fullName, forkTime };
            }

        } catch (IOException | org.json.JSONException e) {
            e.printStackTrace();
            // Handle exceptions appropriately
        }

        return null; // If not forked from another repository
    }

    public Set<String> findModifiedFilesAfterCreation(String owner, String repo) throws IOException {
        Instant creationDate = getRepoCreationDate(owner, repo);

        Set<String> modifiedFiles = new HashSet<>();
        String commitsUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/commits?since=" + creationDate.toString();

        while (commitsUrl != null) {
            JSONObject response = getJson(commitsUrl);
            JSONArray commits = response.getJSONArray("data");

            for (int i = 0; i < commits.length(); i++) {
                JSONObject commit = commits.getJSONObject(i);
                String sha = commit.getString("sha");
                modifiedFiles.addAll(getFilesInCommit(owner, repo, sha));
            }

            commitsUrl = response.optString("nextLink", null);
        }

        return modifiedFiles;
    }

    private Instant getRepoCreationDate(String owner, String repo) throws IOException {
        JSONObject repoJson = getJson("https://api.github.com/repos/" + owner + "/" + repo).getJSONObject("data");
        return Instant.parse(repoJson.getString("created_at"));
    }

    private Set<String> getFilesInCommit(String owner, String repo, String sha) throws IOException {
        JSONObject commitJson = getJson("https://api.github.com/repos/" + owner + "/" + repo + "/commits/" + sha).getJSONObject("data");
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

    private JSONObject getJson(String url) throws IOException {
        HttpClient client = HttpClients.createDefault();
        HttpGet request = new HttpGet(url);
        request.addHeader("Authorization", "Bearer " + accessToken);
        request.addHeader("Accept", "application/vnd.github.v3+json");

        HttpResponse response = client.execute(request);
        HttpEntity entity = response.getEntity();
        String responseBody = EntityUtils.toString(entity);

        // Parse pagination headers if present
        String linkHeader = response.getFirstHeader("Link") != null ? response.getFirstHeader("Link").getValue() : null;
        String nextLink = null;

        if (linkHeader != null) {
            for (String part : linkHeader.split(",")) {
                if (part.contains("rel=\"next\"")) {
                    nextLink = part.substring(part.indexOf("<") + 1, part.indexOf(">"));
                }
            }
        }

        JSONObject result = new JSONObject();
        result.put("data", new JSONArray(responseBody));
        if (nextLink != null) result.put("nextLink", nextLink);

        return result;
    }

    public List<String> fetchForkSelection(String repository) {
        // Step 1: Fetch all forks (parent, siblings, children)
        Set<String> candidateForks = fetchAllForks(repository);

        // Step 2: Filter and collect metadata
        List<JSONObject> qualifiedForks = new ArrayList<>();
        for (String repo : candidateForks) {
            try {
                JSONObject repoJson = getJson("https://api.github.com/repos/" + repo).getJSONObject("data");

                // ASE paper checks
                String forkDefaultBranch = repoJson.getString("default_branch");
                if (repoJson.has("parent")) {
                    String parentRepo = repoJson.getJSONObject("parent").getString("full_name");
                    String parentDefaultBranch = getJson("https://api.github.com/repos/" + parentRepo).getJSONObject("data").getString("default_branch");
                    if (!hasAtLeast5ForkSpecificCommits(repo, parentDefaultBranch, forkDefaultBranch)) continue;
                    if (!hasAtLeast30DaysEvolution(repo, parentDefaultBranch, forkDefaultBranch)) continue;
                }
                if (wasDiscontinuedAfterMerge(repo)) continue;

                // Collect metadata
                JSONArray commits = getJson("https://api.github.com/repos/" + repo + "/commits").getJSONArray("data");
                int contributorsCount = getJson("https://api.github.com/repos/" + repo + "/contributors").getJSONArray("data").length();

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

    private Set<String> fetchAllForks(String repository) {
        Set<String> forks = new HashSet<>();

        // Step 1: Find the parent
        String[] parentInfo = fetchForkedOff(repository);
        if (parentInfo != null) {
            String parentRepo = parentInfo[0];
            forks.add(parentRepo);

            // Step 2: Add siblings
            for (String[] fork : fetchForks(parentRepo)) {
                forks.add(fork[0]);
            }
            forks.remove(repository);
        }

        // Step 3: Add children
        for (String[] fork : fetchForks(repository)){
            forks.add(fork[0]);
        }
        return forks;
    }

    private boolean hasAtLeast5ForkSpecificCommits(String repository, String parentDefaultBranch, String forkDefaultBranch) {
        try {
            // 1. Get parent info
            String[] parentInfo = fetchForkedOff(repository);
            if (parentInfo == null) return true;

            String parentRepo = parentInfo[0];
            String forkOwner = repository.split("/")[0];

            // 2. Get fork creation date
            Instant forkCreationDate = Instant.parse(parentInfo[1]);

            // 3. Rough prefilter: commits since creation
            JSONArray commits = getJson("https://api.github.com/repos/" + repository + "/commits?since=" + forkCreationDate.toString()).getJSONArray("data");
            if (commits.length() < 5) return false;

            // 4. Accurate fork-specific check with /compare/
            String compareUrl = "https://api.github.com/repos/" + parentRepo + "/compare/" + parentDefaultBranch + "..." + forkOwner + ":" + forkDefaultBranch;
            JSONObject compareJson = getJson(compareUrl).getJSONObject("data");
            int aheadBy = compareJson.getInt("ahead_by");

            return aheadBy >= 5;

        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasAtLeast30DaysEvolution(String repository, String parentDefaultBranch, String forkDefaultBranch) {
        try {
            String[] parentInfo = fetchForkedOff(repository);
            if (parentInfo == null) {
                JSONArray commits = getJson("https://api.github.com/repos/" + repository + "/commits").getJSONArray("data"); // ToDo: pagination still missing!
                if (commits.length() < 2) return false;

                Instant first = Instant.parse(commits.getJSONObject(commits.length() - 1).getJSONObject("commit").getJSONObject("committer").getString("date"));
                Instant last = Instant.parse(commits.getJSONObject(0).getJSONObject("commit").getJSONObject("committer").getString("date"));

                return Duration.between(first, last).toDays() >= 30;
            } else {
                String parentRepo = parentInfo[0];
                String forkOwner = repository.split("/")[0];

                // Compare parent...fork to get only fork-specific commits
                String compareUrl = "https://api.github.com/repos/" + parentRepo + "/compare/" + parentDefaultBranch + "..." + forkOwner + ":" + forkDefaultBranch;
                JSONObject compareJson = getJson(compareUrl).getJSONObject("data");

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
            JSONObject response = getJson(commitsUrl);
            JSONArray commits = response.getJSONArray("data");

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
}
