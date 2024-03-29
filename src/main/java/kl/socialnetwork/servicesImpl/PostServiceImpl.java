package kl.socialnetwork.servicesImpl;

import kl.socialnetwork.domain.entities.Post;
import kl.socialnetwork.domain.entities.Relationship;
import kl.socialnetwork.domain.entities.User;
import kl.socialnetwork.domain.entities.UserRole;
import kl.socialnetwork.domain.models.bindingModels.post.PostCreateBindingModel;
import kl.socialnetwork.domain.models.serviceModels.PostServiceModel;
import kl.socialnetwork.domain.models.serviceModels.RelationshipServiceModel;
import kl.socialnetwork.repositories.PostRepository;
import kl.socialnetwork.repositories.RoleRepository;
import kl.socialnetwork.repositories.UserRepository;
import kl.socialnetwork.services.PostService;
import kl.socialnetwork.services.RelationshipService;
import kl.socialnetwork.validations.serviceValidation.services.PostValidationService;
import kl.socialnetwork.validations.serviceValidation.services.UserValidationService;

import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static kl.socialnetwork.utils.constants.ResponseMessageConstants.SERVER_ERROR_MESSAGE;

@Service
@Transactional
public class PostServiceImpl implements PostService {
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final ModelMapper modelMapper;
    private final PostValidationService postValidationService;
    private final UserValidationService userValidationService;
    private final RelationshipService relationshipService;
    @Autowired
    public PostServiceImpl(PostRepository postRepository, UserRepository userRepository,  RoleRepository roleRepository, ModelMapper modelMapper,
                           RelationshipService relationshipService, PostValidationService postValidationService, UserValidationService userValidationService) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.modelMapper = modelMapper;
        this.postValidationService = postValidationService;
        this.userValidationService = userValidationService;
        this.relationshipService= relationshipService;
    }

    @Override
    public boolean createPost(PostCreateBindingModel postCreateBindingModel) throws Exception {
        if (!postValidationService.isValid(postCreateBindingModel)) {
            throw new Exception(SERVER_ERROR_MESSAGE);
        }

        User loggedInUser = this.userRepository
                .findById(postCreateBindingModel.getLoggedInUserId())
                .filter(userValidationService::isValid)
                .orElseThrow(Exception::new);

        User timelineUser = this.userRepository
                .findById(postCreateBindingModel.getTimelineUserId())
                .filter(userValidationService::isValid)
                .orElseThrow(Exception::new);

        PostServiceModel postServiceModel = new PostServiceModel();
        postServiceModel.setLoggedInUser(loggedInUser);
        postServiceModel.setTimelineUser(timelineUser);
        postServiceModel.setContent(postCreateBindingModel.getContent());
        postServiceModel.setImageUrl(postCreateBindingModel.getImageUrl());
        postServiceModel.setTime(LocalDateTime.now());


        Post post = this.modelMapper.map(postServiceModel, Post.class);

        if (postValidationService.isValid(post)) {
            return this.postRepository.save(post) != null;
        }
        return false;
    }

    @Override
    public List<PostServiceModel> getAllPosts(String timelineUserId) {
        List<Post> postList = this.postRepository.findAllByTimelineUserIdOrderByTimeDesc(timelineUserId);

        return postList
                .stream()
                .map(post -> this.modelMapper
                        .map(post, PostServiceModel.class))

                .collect(Collectors.toList());
    }

    @Override
    public List<PostServiceModel> getAllFriendsPosts(String loggInUserId) throws Exception {
        List<PostServiceModel> postsListQuery = new ArrayList<>();
        List<RelationshipServiceModel> relList =relationshipService.findAllUserRelationshipsWithStatus(loggInUserId);
        for (RelationshipServiceModel rel: relList){
            String id = rel.getUserTwo().getId();
            List <PostServiceModel> postsList = getAllPosts(id);
            for (PostServiceModel post: postsList){
                postsListQuery.add(post);
            }
        }
        return postsListQuery;
    }

    @Async
    @Override
    public CompletableFuture<Boolean> deletePost(String loggedInUserId, String postToRemoveId) throws Exception {
        User loggedInUser = this.userRepository.findById(loggedInUserId).orElse(null);
        Post postToRemove = this.postRepository.findById(postToRemoveId).orElse(null);

        if (!userValidationService.isValid(loggedInUser) || !postValidationService.isValid(postToRemove)) {
            throw new Exception(SERVER_ERROR_MESSAGE);
        }

        UserRole rootRole = this.roleRepository.findByAuthority("ROOT");
        boolean hasRootAuthority = loggedInUser.getAuthorities().contains(rootRole);
        boolean isPostCreator = postToRemove.getLoggedInUser().getId().equals(loggedInUserId);
        boolean isTimeLineUser = postToRemove.getTimelineUser().getId().equals(loggedInUserId);

        if (hasRootAuthority || isPostCreator || isTimeLineUser) {
            try {
                this.postRepository.delete(postToRemove);
                return CompletableFuture.completedFuture(true);
            } catch (Exception e) {
                throw new Exception(SERVER_ERROR_MESSAGE);
            }
        } else {
            throw new Exception(SERVER_ERROR_MESSAGE);
        }
    }
}
