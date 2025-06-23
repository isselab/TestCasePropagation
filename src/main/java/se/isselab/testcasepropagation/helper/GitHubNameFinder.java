package se.isselab.testcasepropagation.helper;

import com.intellij.openapi.project.Project;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;

public class GitHubNameFinder {

    public static String getGitHubName(Project project) {
        GitRepositoryManager repositoryManager = GitRepositoryManager.getInstance(project);
        GitRepository repository = repositoryManager.getRepositories().stream().findFirst().orElse(null);

        if (repository == null) {
            return "No Git repository found";
        }

        for (GitRemote remote : repository.getRemotes()) {
            for (String url : remote.getUrls()) {
                String fullName = parseGitHubUrl(url);
                if (fullName != null) {
                    return fullName;
                }
            }
        }
        return "No GitHub remote found";
    }

    private static String parseGitHubUrl(String url) {
        // Supports SSH and HTTPS GitHub URLs
        String regex = "github\\.com[:/](.+?)/(.+?)(?:\\.git)?$";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(regex).matcher(url);
        if (matcher.find()) {
            return matcher.group(1) + "/" + matcher.group(2);
        }
        return null;
    }
}
