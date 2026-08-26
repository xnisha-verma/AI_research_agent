package com.research.AIagent.service;

import com.research.AIagent.model.Platform;
import com.research.AIagent.model.ScrapedPost;
import com.research.AIagent.model.TrendAnalysis;
import com.research.AIagent.model.TrendTopic;
import com.research.AIagent.reposistory.TrendAnalysisRepository;
import com.research.AIagent.reposistory.TrendTopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class LlmAnalysisService {
    private static final String SYSTEM_PROMPT= """
                   You are an AI research analyst specializing in technology trends and startup ideas.

        You will receive a batch of posts from Reddit, Hacker News, and Product Hunt.

        Analyze them and identify the top emerging trends, recurring topics, and notable signals.

        For each trend you identify, respond with ONLY a JSON array (no markdown, no preamble):

        [
          {
            "topic": "Short topic name",
            "summary": "2-3 sentence summary of the trend",
            "reasoning": "Why this is significant and why it's trending now",
            "category": "One of: AI/ML, DevTools, SaaS, Infrastructure, Security, Web3, Hardware, Other",
            "mentionCount": 5,
            "trendScore": 0.85,
            "primaryPlatform": "REDDIT",
            "relatedPostIds": ["ext_id_1", "ext_id_2"]
          }
        ]

        Rules:
        - Identify 5-10 trends maximum
        - trendScore is 0.0 to 1.0 (1.0 = extremely strong signal)
        - mentionCount = how many posts in this batch relate to this trend
        - Only include trends mentioned in 2+ posts OR with very high engagement (score > 200)
        - Focus on EMERGING trends, not well-established topics
        - primaryPlatform = where the trend is strongest
        """;
    private final WebClient groqWebClient;
    private final TrendAnalysisRepository analysisRepository;
    private final TrendTopicRepository topicRepository;
    private final ObjectMapper objectMapper;

    @Value("${groq.model}")
    private String model;


    public TrendAnalysis analyze(final List<ScrapedPost> posts){
        final String userPrompt = buildPrompt(posts);
        String rawResponse = null;
        for(int i=1;i<=3;i++){
            try{
                String uri;
                Map<String, Object> requestBody;
                
                if (i == 1) {
                    // Attempt 1: Standard OpenAI/Groq Chat Completions Format
                    uri = "/chat/completions";
                    requestBody = Map.of(
                         "model", model,
                         "messages", List.of(
                             Map.of("role", "system", "content", SYSTEM_PROMPT),
                             Map.of("role", "user", "content", userPrompt)
                         )
                    );
                } else if (i == 2) {
                    // Attempt 2: Anthropic-style messages Format as fallback
                    uri = "/messages";
                    requestBody = Map.of(
                         "model", model,
                         "max_tokens", 4000,
                         "system", SYSTEM_PROMPT,
                         "messages", List.of(Map.of("role","user","content",userPrompt))
                    );
                } else {
                    // Attempt 3: Retry OpenAI/Groq
                    uri = "/chat/completions";
                    requestBody = Map.of(
                         "model", model,
                         "messages", List.of(
                             Map.of("role", "system", "content", SYSTEM_PROMPT),
                             Map.of("role", "user", "content", userPrompt)
                         )
                    );
                }

                log.info("Sending request to LLM endpoint: {}", uri);
                rawResponse = this.groqWebClient.post()
                        .uri(uri)
                        .bodyValue(requestBody)
                        .retrieve()
                        .bodyToMono(String.class)
                        .block();
                if (rawResponse != null) {
                    break;
                }
            }catch (final Exception e){
                log.warn("Groq attempt {}/3 failed: {}", i, e.getMessage());
                if(i<3){
                    try{
                        Thread.sleep(2000);
                    }catch (final InterruptedException ie){
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        boolean isFallback = false;
        if(rawResponse==null){
            log.error("All groq attempts failed to respond");
            rawResponse="{}";
            isFallback = true;
        }

        TrendAnalysis analysis = TrendAnalysis.builder()
                .rawAnalysis(rawResponse)
                .postAnlaysis(posts.size())
                .build();
        analysis = this.analysisRepository.save(analysis);

        List<TrendTopic> topics = new ArrayList<>();
        if (!isFallback) {
            topics = parseTopics(rawResponse, analysis);
        }

        if (topics.isEmpty()) {
            log.info("No LLM trends parsed; using local fallback trend generator.");
            topics = generateFallbackTrends(posts, analysis);
        }

        this.topicRepository.saveAll(topics);
        log.info("Saved {} trend topics from analysis of {} posts.", topics.size(), posts.size());
        return analysis;
    }

    private List<TrendTopic> parseTopics(String rawResponse, TrendAnalysis analysis) {
        final List<TrendTopic> topics = new ArrayList<>();
        try{
            final JsonNode root = this.objectMapper.readTree(rawResponse);
            String content = null;

            // 1. Try OpenAI/Groq style response: choices[0].message.content
            if (root.has("choices") && root.path("choices").isArray() && !root.path("choices").isEmpty()) {
                content = root.path("choices").get(0).path("message").path("content").asText("").strip();
            }
            // 2. Try Anthropic style response: content[0].text
            else if (root.has("content") && root.path("content").isArray() && !root.path("content").isEmpty()) {
                content = root.path("content").get(0).path("text").asText("").strip();
            }
            // 3. Fallback: maybe response is the raw content itself
            else {
                content = rawResponse.strip();
            }

            if (content == null || content.isBlank()) {
                log.error("Could not extract text content from LLM response: {}", rawResponse);
                return topics;
            }

            if(content.startsWith("```")){
                 content = content.replaceFirst("^```[a-zA-Z]*\\n", "").trim();
                 if (content.endsWith("```")) {
                     content = content.substring(0, content.length() - 3).trim();
                 }
            }
            final JsonNode trendsArray = this.objectMapper.readTree(content);
            if(!trendsArray.isArray()) return topics;
            for(final JsonNode node: trendsArray){
                try{
                    final Platform platform = parsePlatform(node.path("primaryPlatform").asText(""));
                    final List<String> relatedIds = new ArrayList<>();
                    final JsonNode relatedNode = node.path("relatedPostIds");
                    if(relatedNode.isArray()){
                        for(final JsonNode id: relatedNode){
                            relatedIds.add(id.asText());
                        }
                    }
                    final TrendTopic topic= TrendTopic.builder()
                            .topic(node.path("topic").asText(""))
                            .summary(node.path("summary").asText(""))
                            .reasoning(node.path("reasoning").asText(""))
                            .category(node.path("category").asText(""))
                            .mentionCount(node.path("mentionCount").asInt(0))
                            .trendScore(node.path("trendScore").asDouble(0.0))
                            .platform(platform)
                            .samplePostIds(String.join(",", relatedIds))
                            .analysis(analysis)
                            .build();
                    topics.add(topic);
                }catch (final Exception e){
                    log.error("Failed to parse trend topic: {}", node, e);
                }
            }
        }catch (final Exception e){
            log.error("Failed to parse Groq response: {}", rawResponse, e);
        }
        return topics;
    }

    public List<TrendTopic> generateFallbackTrends(List<ScrapedPost> posts, TrendAnalysis analysis) {
        log.info("Generating fallback trends locally from {} posts", posts.size());
        final List<TrendTopic> topics = new ArrayList<>();
        
        final Map<String, List<String>> categoryKeywords = Map.of(
            "AI/ML", List.of("ai", "ml", "llm", "artificial", "chatgpt", "gpt", "model", "neural", "training", "openai", "llama", "deepseek", "claude"),
            "DevTools", List.of("developer", "dev", "github", "git", "framework", "library", "coding", "tool", "api", "rust", "typescript", "npm", "python"),
            "SaaS", List.of("saas", "startup", "product", "business", "customer", "pricing", "revenue", "mrr", "marketing", "launch"),
            "Infrastructure", List.of("cloud", "server", "database", "aws", "docker", "kubernetes", "deploy", "hosting", "sql", "redis"),
            "Security", List.of("security", "hack", "auth", "oauth", "vulnerability", "encrypt", "password", "leak"),
            "Web3", List.of("crypto", "blockchain", "web3", "nft", "bitcoin", "ethereum", "solana")
        );
        
        for (final Map.Entry<String, List<String>> entry : categoryKeywords.entrySet()) {
            final String category = entry.getKey();
            final List<String> keywords = entry.getValue();
            
            final List<ScrapedPost> matchingPosts = new ArrayList<>();
            for (final ScrapedPost post : posts) {
                final String text = (post.getTitle() + " " + (post.getContent() != null ? post.getContent() : "")).toLowerCase();
                for (final String kw : keywords) {
                    if (text.contains(kw)) {
                        matchingPosts.add(post);
                        break;
                    }
                }
            }
            
            if (matchingPosts.size() >= 2) {
                final List<String> sampleIds = new ArrayList<>();
                for (int i = 0; i < Math.min(matchingPosts.size(), 3); i++) {
                    sampleIds.add(matchingPosts.get(i).getExternalId());
                }
                
                Platform primaryPlatform = Platform.REDDIT;
                long redditCount = matchingPosts.stream().filter(p -> p.getPlatform() == Platform.REDDIT).count();
                long hnCount = matchingPosts.stream().filter(p -> p.getPlatform() == Platform.HACKERNEWS).count();
                long phCount = matchingPosts.stream().filter(p -> p.getPlatform() == Platform.PRODUCTHUNT).count();
                if (hnCount >= redditCount && hnCount >= phCount) primaryPlatform = Platform.HACKERNEWS;
                else if (phCount >= redditCount && phCount >= hnCount) primaryPlatform = Platform.PRODUCTHUNT;
                
                final String title1 = matchingPosts.get(0).getTitle();
                final String title2 = matchingPosts.get(1).getTitle();
                final String topicName = "Emerging " + category + " Activity";
                
                final String summary = String.format("Increased discussion regarding %s tools and technologies. Notable references include: '%s' and '%s'.", 
                                                category, 
                                                title1.length() > 50 ? title1.substring(0, 47) + "..." : title1,
                                                title2.length() > 50 ? title2.substring(0, 47) + "..." : title2);
                
                final String reasoning = String.format("Detected %d recent posts across platforms indicating active community interest in this domain.", matchingPosts.size());
                
                final TrendTopic topic = TrendTopic.builder()
                        .topic(topicName)
                        .summary(summary)
                        .reasoning(reasoning)
                        .category(category)
                        .mentionCount(matchingPosts.size())
                        .trendScore(Math.min(0.5 + (matchingPosts.size() * 0.05), 0.95))
                        .platform(primaryPlatform)
                        .samplePostIds(String.join(",", sampleIds))
                        .analysis(analysis)
                        .build();
                        
                topics.add(topic);
            }
        }
        
        if (topics.isEmpty() && !posts.isEmpty()) {
            final List<String> sampleIds = new ArrayList<>();
            for (int i = 0; i < Math.min(posts.size(), 3); i++) {
                sampleIds.add(posts.get(i).getExternalId());
            }
            final TrendTopic topic = TrendTopic.builder()
                    .topic("General Tech & Discussion Trends")
                    .summary("Aggregated activity across technology platforms showing general development and startup interest.")
                    .reasoning("Synthesized from all recent scraped posts across Reddit, Hacker News, and Product Hunt.")
                    .category("Other")
                    .mentionCount(posts.size())
                    .trendScore(0.50)
                    .platform(posts.get(0).getPlatform())
                    .samplePostIds(String.join(",", sampleIds))
                    .analysis(analysis)
                    .build();
            topics.add(topic);
        }
        
        return topics;
    }

    private Platform parsePlatform(final String primaryPlatform){
        try{
            return Platform.valueOf(primaryPlatform.toUpperCase());
        }catch (final Exception e){
            return null;
        }
    }

    private String buildPrompt(final List<ScrapedPost> posts){
        final StringBuilder prompt = new StringBuilder("Here are the latest posts from the tech communities:\n\n ");
        for(final ScrapedPost post:posts){
            prompt.append(String.format("[%s] (score: %d, comments: %d) %s%n",
                    post.getPlatform(),
                    post.getScore(),
                    post.getCommentCount(),
                    post.getTitle()
                    ));
            if(post.getContent()!=null && !post.getContent().isBlank()){
                final String snippet = post.getContent().length()>200
                        ? post.getContent().substring(0,200)+"..."
                        : post.getContent();
                prompt.append(" > ").append(snippet).append("\n");
            }
            prompt.append("\n");
        }
        return prompt.toString();
    }
}
