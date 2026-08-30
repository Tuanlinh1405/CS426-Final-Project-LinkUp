# Business Analysis for LinkUp Database

This document outlines the data requirements and business rules for the 'LinkUp' social network application.

## 1. User Management & Authentication

### Entities
- **User**: Represents a registered user.
    - Attributes: Full Name, Username, Email, Password (hashed), Birthdate, Gender, Created At, Updated At.
- **RefreshToken**: Stores tokens for persistent login sessions.
    - Attributes: Token String, User ID, Expiry Date, Created At.

### Business Rules
- Username and Email must be unique.
- Passwords must be hashed before storage.
- A user must be at least a certain age (e.g., 13) to register (to be enforced by app logic).

---

## 2. Profile Management

### Entities
- **Profile**: Extended user information (often merged with User or as a separate table).
    - Attributes: User ID, Bio, Avatar URL, Cover Photo URL, Follower Count (denormalized for performance), Following Count.
- **Follow**: Represents a relationship between two users.
    - Attributes: Follower ID, Following ID, Created At.

### Business Rules
- A user cannot follow themselves.
- Following is a one-way relationship unless the other user follows back.

---

## 3. Social Media (Posts, Comments, Reactions)

### Entities
- **Post**: A status update or media share.
    - Attributes: Author ID, Content Text, Privacy Level (Public, Friends, Only Me), Created At, Updated At.
- **PostMedia**: Media files attached to a post.
    - Attributes: Post ID, Media ID, Order/Index.
- **Comment**: User feedback on a post.
    - Attributes: Post ID, Author ID, Content Text, Created At.
- **PostReaction**: Likes or other reactions.
    - Attributes: Post ID, User ID, Reaction Type (LIKE, LOVE, etc.), Created At.

### Business Rules
- A user can react only once per post.
- Deleting a post should archive or delete associated media and comments.
- Comments can only be edited or deleted by the author.

---

## 4. Reels (Short Videos)

### Entities
- **Reel**: A short vertical video.
    - Attributes: Author ID, Caption, Video URL, Thumbnail URL, Duration, Width, Height, Created At.
- **ReelReaction**: Likes on reels.
    - Attributes: Reel ID, User ID, Created At.
- **ReelComment**: Comments on reels.
    - Attributes: Reel ID, Author ID, Content Text, Created At.

### Business Rules
- Reels are public by default.
- Video files are stored in MinIO; only metadata and keys are in the database.

---

## 5. Realtime Chat

### Entities
- **Conversation**: A chat session between users.
    - Attributes: Type (DIRECT, GROUP), Created At, Updated At.
- **ConversationMember**: Participants in a conversation.
    - Attributes: Conversation ID, User ID, Joined At, Last Read At.
- **Message**: Individual messages in a chat.
    - Attributes: Conversation ID, Sender ID, Message Type (TEXT, IMAGE, VIDEO), Text Content, Media ID, Created At.

### Business Rules
- For Direct Messages (1-1), only one active conversation should exist between two users.
- Messages cannot be edited once sent (typical for this scope).
- Users can only see messages in conversations they are members of.

---

## 6. AI Assistant

### Entities
- **AIConversation**: A session with the Meta-like AI.
    - Attributes: User ID, Title, Created At, Updated At.
- **AIMessage**: Messages exchanged with the AI.
    - Attributes: AI Conversation ID, Role (USER, ASSISTANT), Content, Created At.

### Business Rules
- AI conversations are private to the user.
- History should be persisted for the user to resume later.

---

## 7. Dating

### Entities
- **DatingProfile**: Profile specifically for the dating feature.
    - Attributes: User ID, Bio, Interests, Preferred Gender, Preferred Age Range, Location.
- **DatingPhoto**: Photos for the dating profile.
    - Attributes: Dating Profile ID, Photo URL, Order.
- **DatingSwipe**: Records a "Like" or "Pass" action.
    - Attributes: Swiper ID, Swiped ID (Target), Direction (LIKE, PASS), Created At.
- **DatingMatch**: Created when two users like each other.
    - Attributes: User 1 ID, User 2 ID, Created At.

### Business Rules
- A "Match" is only created when there is a mutual "LIKE" between two users.
- A user cannot swipe on their own dating profile.
- Users can only have one dating profile.

---

## 8. Notifications

### Entities
- **Notification**: Alerts for user activity.
    - Attributes: Recipient ID, Actor ID, Type (LIKE, COMMENT, FOLLOW, MATCH), Target ID (Post ID, Reel ID, etc.), Is Read, Created At.

### Business Rules
- Notifications should be delivered in realtime via WebSocket if the user is online.
- Users should be able to mark notifications as read.

---

## 9. Media Metadata (General)

### Entities
- **Media**: Centralized storage info for all files.
    - Attributes: Owner ID, Storage Key (MinIO), Mime Type, File Size, Dimensions (Width/Height), Created At.

### Business Rules
- All media uploads must be validated for type and size.
- Storage keys should be unique (UUID-based).
