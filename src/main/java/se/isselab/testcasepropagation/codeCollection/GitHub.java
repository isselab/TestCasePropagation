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
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import se.isselab.testcasepropagation.intelliJ.settings.TestCasePropagationSettings;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.net.SocketTimeoutException;

public class GitHub {
    private final String accessToken;
    private final Map<String, Object> cache = new HashMap<>();
    private HttpClient client;

    public GitHub(String accessToken) {
        this.accessToken = accessToken;
        
        // Connection pool configuration
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(100);                 // Maximum total connections
        cm.setDefaultMaxPerRoute(20);        // Maximum connections per route
        
        // Request configuration
        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(30000)        // 30 seconds connection timeout
            .setSocketTimeout(30000)         // 30 seconds socket timeout
            .setConnectionRequestTimeout(30000)
            .build();
        
        // Build HttpClient with connection pool
        this.client = HttpClients.custom()
            .setConnectionManager(cm)
            .setDefaultRequestConfig(requestConfig)
            .build();
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





    public List<String> fetchForkSelection(String repository) throws IOException, InterruptedException {
        System.out.println(Instant.now() + " || About to start: fetchAllForks()");
        List<JSONObject> forks = fetchAllForks(repository);

        System.out.println(Instant.now() + " || About to start: filterForks()");
        List<JSONObject> filtered = filterForks(forks, repository);

        System.out.println(Instant.now() + " || About to start: fetchMetaData()");
        List<JSONObject> metadatas = fetchMetaData(filtered);
        int topN = 80;


        System.out.println(Instant.now() + " || About to start: rankForks()");
        return rankForks(metadatas, topN);
    }

    public List<JSONObject> fetchAllForks(String repository) throws IOException, InterruptedException {
        List<JSONObject> forks = new ArrayList<>();

        // Step 1: Find the parent
        JSONObject repo = getJsonObject("https://api.github.com/repos/" + repository);
        if (repo != null && repo.has("parent")) {
            String parentFullName = repo.getJSONObject("parent").getString("full_name");
            forks.add(getJsonObject("https://api.github.com/repos/" + parentFullName));
            System.out.println("Foung parant and added to list: " + parentFullName);

            // Step 2: Add siblings
            for (Object fork : getPaginatedJsonArray("https://api.github.com/repos/" + parentFullName + "/forks")) {
                forks.add((JSONObject) fork);
                System.out.println("Foung sibling and added to list: " + ((JSONObject) fork).getString("full_name"));
            }
            forks.remove(repo);
        }

        // Step 3: Add children
        for (Object fork : getJsonArray("https://api.github.com/repos/" + repository + "/forks")) {
            forks.add((JSONObject) fork);
            System.out.println("Foung child and added to list: " + ((JSONObject) fork).getString("full_name"));
        }

        return forks;
    }

    private List<JSONObject> filterForks(List<JSONObject> forks, String repository) throws InterruptedException {
        List<JSONObject> result = new ArrayList<>();

        for (JSONObject fork : forks) {
            if (!fork.has("parent") || fork.getJSONObject("parent").isEmpty()) {
                result.add(fork);
                continue;
            }
            String parentDefaultBranch = fork.getJSONObject("parent").getString("default_branch");
            String forkDefaultBranch = fork.getString("default_branch");

            // ASE paper checks
            if (hasAtLeast5ForkSpecificCommits(fork, parentDefaultBranch, forkDefaultBranch) &&
                hasAtLeast30DaysEvolution(fork, parentDefaultBranch, forkDefaultBranch) &&
                !wasDiscontinuedAfterMerge(fork.getString("full_name"))) {

                result.add(fork);
            } else {
                cache.remove("https://api.github.com/repos/" + fork.getString("full_name"));
            }
        }
        return result;
    }


    private List<JSONObject> fetchMetaData(List<JSONObject> forks) throws IOException, InterruptedException {
        List<JSONObject> result = new ArrayList<>();
        for (JSONObject fork : forks) {

            System.out.println("Finding contributor count for fork: " + fork.getString("full_name"));
            fork.put("contributors_count", fetchContributorsCount(fork));

            System.out.println("Finding commits count for fork: " + fork.getString("full_name"));
            fork.put("commits_count", fetchCommitsCount(fork));
            result.add(fork);
        }
        return result;
    }

    private int fetchContributorsCount(JSONObject repo) throws IOException, InterruptedException {
        HttpResponse response = null;
        try {
            response = sendGetRequest("https://api.github.com/repos/" + repo.getString("full_name") + "/contributors?per_page=1");
            HttpEntity entity = response.getEntity();
            EntityUtils.consume(entity); // release connection
            return extractCountFromLinkHeader(response);
        } finally {
            if (response instanceof CloseableHttpResponse) {
                ((CloseableHttpResponse) response).close();
            }
        }
    }

    private int fetchCommitsCount(JSONObject repo) throws IOException, InterruptedException {
        HttpResponse response = null;
        try {
            response = sendGetRequest("https://api.github.com/repos/" + repo.getString("full_name") + "/commits?per_page=1");
            HttpEntity entity = response.getEntity();
            EntityUtils.consume(entity); // release connection
            return extractCountFromLinkHeader(response);
        } finally {
            if (response instanceof CloseableHttpResponse) {
                ((CloseableHttpResponse) response).close();
            }
        }
    }

    private int extractCountFromLinkHeader(HttpResponse response) {
        System.out.println("\nStart extracting count from Link Header:");

        String link = Arrays.stream(response.getAllHeaders())
                .filter(h -> h.getName().equals("Link"))
                .map(org.apache.http.Header::getValue)
                .findFirst().orElse(null);

        System.out.println(link + "\n");

        if (link == null || !link.contains("rel=\"last\"")) return 1;

        System.out.println("Now the lastUrl:");

        String lastUrl = Arrays.stream(link.split(","))
                .filter(s -> s.contains("rel=\"last\""))
                .map(s -> s.substring(s.indexOf("<") + 1, s.indexOf(">")))
                .findFirst().orElse(null);

        System.out.println(lastUrl + "\n");

        if (lastUrl == null) return 1;

        try {
            URI uri = new URI(lastUrl);
            Map<String, String> queryParams = Arrays.stream(uri.getQuery().split("&"))
                    .map(param -> param.split("="))
                    .collect(Collectors.toMap(
                            param -> param[0],
                            param -> param[1]
                    ));

            return queryParams.containsKey("page") ? Integer.parseInt(queryParams.get("page")) : 1;

        } catch (URISyntaxException | NumberFormatException e) {
            return 1;
        }
    }


    private List<String> rankForks(List<JSONObject> forks, int topN) {;
        // Step 3: Sort by commits, contributors, stars, forks
        forks.sort((a, b) -> {
            int cmp = Integer.compare(b.getInt("commits_count"), a.getInt("commits_count"));
            if (cmp != 0) return cmp;
            cmp = Integer.compare(b.getInt("contributors_count"), a.getInt("contributors_count"));
            if (cmp != 0) return cmp;
            cmp = Integer.compare(b.getInt("stargazers_count"), a.getInt("stargazers_count"));
            if (cmp != 0) return cmp;
            return Integer.compare(b.getInt("forks_count"), a.getInt("forks_count"));
        });

        // Step 4: Return up to top 100
        return forks.stream().limit(topN).map(f -> f.getString("full_name")).toList();
    }



    private boolean hasAtLeast5ForkSpecificCommits(JSONObject repository, String parentDefaultBranch, String forkDefaultBranch) {
        try {
            // 1. Get parent info
            String parentFullName = repository.getJSONObject("parent").getString("full_name");

            String forkOwner = repository.getJSONObject("owner").getString("login");

            // 2. Get fork creation date
            Instant forkCreationDate = Instant.parse(repository.getString("created_at"));

            // 3. Rough prefilter: commits since creation
            JSONArray commits = getPaginatedJsonArray("https://api.github.com/repos/" + repository.getString("full_name") + "/commits?since=" + forkCreationDate.toString());
            if (commits.length() < 5) return false;

            // 4. Accurate fork-specific check with /compare/
            String compareUrl = "https://api.github.com/repos/" + parentFullName + "/compare/" + parentDefaultBranch + "..." + forkOwner + ":" + forkDefaultBranch;
            JSONObject compareJson = getJsonObject(compareUrl);
            int aheadBy = compareJson.getInt("ahead_by");

            return aheadBy >= 5;

        } catch (Exception e) {
            System.out.println("Error in hasAtLeast5ForkSpecificCommits for repository: " + repository.optString("full_name", "unknown") + ": " + e.getMessage());
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
        int maxRetries = 3;
        int currentTry = 0;
        
        while (currentTry < maxRetries) {
            try {
                HttpGet request = new HttpGet(url);
                request.addHeader("Authorization", "Bearer " + accessToken);
                request.addHeader("Accept", "application/vnd.github.v3+json");
                HttpResponse response = client.execute(request);
            
                int statusCode = response.getStatusLine().getStatusCode();
            
                // Handle rate limiting
                if (statusCode == 403 && response.containsHeader("X-RateLimit-Remaining") &&
                    response.getFirstHeader("X-RateLimit-Remaining").getValue().equals("0")) {
                
                    long resetTime = Long.parseLong(response.getFirstHeader("X-RateLimit-Reset").getValue());
                    long waitTime = resetTime - Instant.now().getEpochSecond() + 1;
                    if (waitTime > 0) {
                        System.out.println("Rate limited. Sleeping for " + waitTime + " seconds.");
                        Thread.sleep(waitTime * 1000);
                        continue; // Retry after waiting
                    }
                }
            
                // Handle other potential error codes
                if (statusCode >= 500) {
                    System.out.println("Server error (status " + statusCode + "). Retrying...");
                    Thread.sleep(1000 * (currentTry + 1)); // Exponential backoff
                    currentTry++;
                    continue;
                }
            
                return response;
            
            } catch (Exception e) {
                System.out.println("Request timed out. Attempt " + (currentTry + 1) + " of " + maxRetries);
                currentTry++;
                if (currentTry >= maxRetries) throw e;
                Thread.sleep(1000 * (currentTry + 1));
            }
        }
        
        throw new IOException("Failed to get response after " + maxRetries + " attempts");
    }

    private JSONObject getJsonObject(String url) throws IOException, InterruptedException {
        System.out.println("In getJsonObject()");
        if (cache.containsKey(url)) return (JSONObject) cache.get(url);

        HttpResponse response = null;
        try {
            response = sendGetRequest(url);
            HttpEntity entity = response.getEntity();
            String body = EntityUtils.toString(entity);

            EntityUtils.consume(entity); // release connection back to pool

            JSONObject object = new JSONObject(body);
            cache.put(url, object);
            System.out.println("About to leave getJsonObject()");
            return object;
        } finally {
            if (response instanceof CloseableHttpResponse) {
                ((CloseableHttpResponse) response).close();
            }
        }
    }

    private JSONArray getJsonArray(String url) throws IOException, InterruptedException {
        if (cache.containsKey(url)) return (JSONArray) cache.get(url);

        HttpResponse response = null;
        try {
            response = sendGetRequest(url);
            HttpEntity entity = response.getEntity();
            String body = EntityUtils.toString(entity);

            EntityUtils.consume(entity); // release connection back to pool

            JSONArray array = new JSONArray(body);
            cache.put(url, array);
            return array;
        } finally {
            if (response instanceof CloseableHttpResponse) {
                ((CloseableHttpResponse) response).close();
            }
        }
    }

    private JSONArray getPaginatedJsonArray(String baseUrl) throws IOException, InterruptedException {
        if (cache.containsKey(baseUrl)) return (JSONArray) cache.get(baseUrl);

        JSONArray all = new JSONArray();
        String url = baseUrl + (baseUrl.contains("?") ? "&" : "?") + "per_page=100";

        while (url != null) {
            HttpResponse response = null;
            try {
                response = sendGetRequest(url);
                HttpEntity entity = response.getEntity();
                String body = EntityUtils.toString(entity);

                EntityUtils.consume(entity); // release connection back to pool

                JSONArray array = new JSONArray(body);
                for (int i = 0; i < array.length(); i++) {
                    all.put(array.get(i));
                }
                url = getNextPageLink(response);
                System.out.println("Next URL found: " + url);
            } finally {
                if (response instanceof CloseableHttpResponse) {
                    ((CloseableHttpResponse) response).close();
                }
            }
        }
        cache.put(baseUrl, all);
        return all;
    }

    private String getNextPageLink(HttpResponse response) {
        Header linkHeader = response.getFirstHeader("Link");
        if (linkHeader == null) return null;

        String[] parts = linkHeader.getValue().split(",\\s*");
        for (String part : parts) {
            String[] sections = part.split(";\\s*");
            if (sections.length == 2 && sections[1].equals("rel=\"next\"")) {
                String url = sections[0].trim();
                if (url.startsWith("<") && url.endsWith(">")) {
                    return url.substring(1, url.length() -1);
                }
            }
        }
        return null;
    }

    public void close() {
        if (client instanceof CloseableHttpClient) {
            try {
                ((CloseableHttpClient) client).close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}