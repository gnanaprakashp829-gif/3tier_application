pipeline {
    agent any

    environment {
        DOCKERHUB_CREDENTIALS = credentials('dockerhub-credentials') // Binds DOCKERHUB_CREDENTIALS_USR & DOCKERHUB_CREDENTIALS_PSW
        EC2_HOST              = credentials('EC2_HOST')             // Secret Text credential ID
        DOCKER_REGISTRY       = 'pgprakash'
        REPO_URL              = 'https://github.com/gnanaprakashp829-gif/3tier_application.git'
        BRANCH                = 'main'
    }

    triggers {
        pollSCM('H/5 * * * *') 
    }

    stages {
        stage('Checkout Code') {
            steps {
                checkout([
                    $class: 'GitSCM',
                    branches: [[name: "*/${BRANCH}"]],
                    userRemoteConfigs: [[url: REPO_URL]]
                ])
            }
        }

        stage('Build and Push Docker Images') {
            steps {
                sh '''
                    set -e
                    echo "================================="
                    echo "Logging into DockerHub"
                    echo "================================="
                    echo "$DOCKERHUB_CREDENTIALS_PSW" | docker login -u "$DOCKERHUB_CREDENTIALS_USR" --password-stdin

                    echo "================================="
                    echo "Building & Pushing Frontend"
                    echo "================================="
                    docker build -t ${DOCKER_REGISTRY}/3tier-frontend:${BUILD_NUMBER} -t ${DOCKER_REGISTRY}/3tier-frontend:latest ./frontend
                    docker push ${DOCKER_REGISTRY}/3tier-frontend:${BUILD_NUMBER}
                    docker push ${DOCKER_REGISTRY}/3tier-frontend:latest

                    echo "================================="
                    echo "Building & Pushing Backend"
                    echo "================================="
                    docker build -t ${DOCKER_REGISTRY}/3tier-backend:${BUILD_NUMBER} -t ${DOCKER_REGISTRY}/3tier-backend:latest ./backend
                    docker push ${DOCKER_REGISTRY}/3tier-backend:${BUILD_NUMBER}
                    docker push ${DOCKER_REGISTRY}/3tier-backend:latest

                    echo "================================="
                    echo "Images pushed successfully"
                    echo "================================="
                '''
            }
        }

        stage('Deploy to EC2') {
            steps {
                sshPublisher(publishers: [
                    sshTransfer(
                        cleanRemote: false,
                        excludes: '',
                        sourceFiles: 'docker-compose.yml,database/init.sql',
                        removePrefix: '',
                        remoteDirectory: '3tier_application',
                        execCommand: '''
                            set -e
                            cd /home/ubuntu/3tier_application

                            echo "================================="
                            echo "Logging into DockerHub on EC2"
                            echo "================================="
                            echo "$DOCKERHUB_CREDENTIALS_PSW" | sudo docker login -u "$DOCKERHUB_CREDENTIALS_USR" --password-stdin

                            echo "================================="
                            echo "Pulling latest images"
                            echo "================================="
                            sudo docker pull pgprakash/3tier-frontend:latest
                            sudo docker pull pgprakash/3tier-backend:latest

                            echo "================================="
                            echo "Stopping old containers"
                            echo "================================="
                            sudo docker compose down --remove-orphans || true

                            echo "================================="
                            echo "Starting new containers"
                            echo "================================="
                            sudo docker compose up -d

                            echo "================================="
                            echo "Waiting for database"
                            echo "================================="
                            sleep 20

                            echo "================================="
                            echo "Container Status"
                            echo "================================="
                            sudo docker compose ps

                            echo "================================="
                            echo "Deployment Complete"
                            echo "================================="
                            echo "Frontend: http://${EC2_HOST}"
                            echo "Backend: http://${EC2_HOST}:5000/health"
                        '''
                    )
                ])
            }
        }
    }
}
