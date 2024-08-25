# GSoC 2024 - Integration of GNU Anastasis With GNU Taler Wallet
This repository contains the final report and work done regarding ***Google Summer of Code, 2024 @[GNU](https://www.gnu.org/software)***.

- Project Title: **Integration of GNU Anastasis With GNU Taler Wallet**
- Contributor: [Amr Salaheddin Abdelhady](https://github.com/amrsalah3)
- Email: amrhady3@gmail.com
- Project Link: [GNU Taler Git Repository](https://git.taler.net/taler-android.git) - [GSoC Website](https://summerofcode.withgoogle.com/programs/2024/projects/boSUJEVt)

## Overview
<p align="justify">The GNU Taler Anastasis is a key backup/recovery system that allows the user to securely deposit shares of a core secret with an open set of escrow providers, to recover it if the secret is lost. The core secret itself is protected from the escrow providers by giving each provider only part of the information, and additionally by encrypting it with an identity-based key unknown to the providers. It prioritizes user privacy by implementing strong cryptographic protocols and privacy-enhancing features. Anastasis comes also with an Android app. 

The GNU Taler Wallet is another software developed within the GNU Taler project. It serves as a user interface for managing digital payments and transactions securely. The app enables users to store digital currency, make payments, and monitor transaction history. The Taler Wallet also comes with an Android app. 

The main objective of the project is to provide a mechanism for any Android app to use the Anastasis app to allow the user back up/recover his secrets, or to use the Wallet app to allow the user to make some payments for the caller app. Eventually, let the Anastasis app use the Wallet app to make the user pay for the backup providers.</p>

## Objectives
1. Design and implement a mechanism to allow any app to save and restore its secret keys via the Taler Anastasis app. **[Completed]**
2. Design and implement a mechanism to allow any app to make its users pay for its services via the Taler Wallet app. **[Completed]**
3. Integrate the Anastasis app with the Wallet app to allow the users to pay the Anastasis backup providers for their service via the Wallet app. **[Completed]**

## Work Done
In this section, I will briefly about my journey throughout the project for people who are interested in getting some background about the current state of the Taler Wallet and the Taler Anastasis projects.

**The first part of the project: (Anastasis integration with other apps)**

The first objective in GSoC 2024 project is to make a communication mechanism for any app to navigate its users to the Anastasis app to back up his secret data of the caller app or recover his secret data. 

What I did here is that I fetched the abandoned Anastasis app project and spent some time trying to rebase it and make it up-to-date with the master to be ready to implement the new features. Then, I implemented the mechanism and made a document describing the Android Intent specification that must be matched and the different results that could be returned to the caller app. In addition, I created an example test app that uses the mechanism. Finally, I created a library to abstract all the mechanism functionalities.

**The second part of the project: (Wallet integration with other apps)**

The Wallet app has been allowing communication with it by the URIs. For example, a user can pay an online merchant by clicking on a taler URI (provided by the merchant) that opens the Wallet app to proceed with the payment. 

What I did here is that I extended this functionality by allowing any merchant app to use Android Intents to send the user to the Wallet app and also return to the merchant (caller) app with a payment result status. Furthermore, I implemented the multi-payment feature which allows the merchant to send multiple "taler pay" URIs in the Intent and these multiple payments will be displayed in the Wallet app for the user and he can pay all of them in one click! In addition, I created an example test app that uses the mechanism. Finally, I created the communication specification data for the Wallet app but this time I extended the RFC document that already has much information about the interaction with the GNU Taler Wallet.

**The third part of the project: (Anastasis integration with the Wallet)**

In the previous state of the Anastasis Android app, it was integrating the main functionalities of the anastasis core. However, backup and recovery via anastasis require that the user pay the backup providers for their service but there was no method provided in the app to do so. 

What I did is that I implemented the paying functionality by linking the Anastasis app to the Wallet app via the mechanism that I created previously. Now, the user can pay the Anastasis providers during the backup (Truths paying & Policies paying). The only thing that's left to do is to implement the paying during recovery (Challenge paying) which I haven't done because the anastasis-core (written in TypeScript) was not already implementing the "ChallengePaying" state, so we can't use this functionality in the Android app yet.

## Final Work Product
- Anastasis Integration With the Other Apps - [Git Branch](https://git.taler.net/taler-android.git/tree/?h=dev/amr-salah/anastasis-integration)
- Anastasis Integration Specification - [Document](https://docs.google.com/document/d/1XQvb1k7QtxtVk4Zcb-0bioCyvnyAZbUidrVeSGRIeO4/)
- Wallet Integration With the Other Apps - [Git Branch](https://git.taler.net/taler-android.git/tree/?h=dev/amr-salah/wallet-payment-integration)
- Wallet Integration Specification - [RFC Document Git Branch](https://git.gnunet.org/lsd0006.git/tree/draft-grothoff-taler.xml?h=dev/amr-salah/gsoc2024-wallet-integration-specs)
- Anastasis Integration With the Wallet - [Git Branch](https://git.taler.net/taler-android.git/tree/?h=dev/amrsalah3/anastasis-wallet-integration)
- Integration demo apps - [Git Branch](https://github.com/amrsalah3/GSoC24-GNU/tree/main/taler-integration-demo-apps)

## Thoughts on GSoC

Lastly, I would like to thank my mentors, Christian Grothoff and Ivan Avalos for their continuous support and help during the project. It was exciting to be working on this interesting project!

